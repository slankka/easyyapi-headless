#!/usr/bin/env python3
"""Exercise the packaged runtime against a fresh Maven reactor and a local YApi stub.

Run: python3 script/smoke-standalone.py build/standalone/easyyapi/bin/easyyapi
Downloads Spring from Maven Central into a temporary repository. Keeps artifacts
and logs in the printed temporary directory to make failures diagnosable.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse


def main():
    launcher = Path(sys.argv[1]).resolve()
    root = Path(tempfile.mkdtemp(prefix="easyyapi-standalone-smoke-"))
    project = root / "project"
    files = {
        "pom.xml": '<project><modelVersion>4.0.0</modelVersion><groupId>smoke</groupId><artifactId>parent</artifactId><version>1</version><packaging>pom</packaging><modules><module>model</module><module>api</module></modules><properties><maven.compiler.release>17</maven.compiler.release></properties></project>',
        "model/pom.xml": '<project><modelVersion>4.0.0</modelVersion><parent><groupId>smoke</groupId><artifactId>parent</artifactId><version>1</version></parent><artifactId>model</artifactId></project>',
        "api/pom.xml": '<project><modelVersion>4.0.0</modelVersion><parent><groupId>smoke</groupId><artifactId>parent</artifactId><version>1</version></parent><artifactId>api</artifactId><dependencies><dependency><groupId>smoke</groupId><artifactId>model</artifactId><version>1</version></dependency><dependency><groupId>org.springframework</groupId><artifactId>spring-web</artifactId><version>6.1.14</version></dependency></dependencies></project>',
        "model/src/main/java/demo/User.java": 'package demo; /** User record. */ public class User { /** Display name. */ public String name; public int age; }',
        "model/src/main/java/demo/Envelope.java": 'package demo; public class Envelope<T> { public T data; public String status; }',
        "api/src/main/java/demo/UserController.java": 'package demo; import demo.User; import demo.Envelope; import org.springframework.web.bind.annotation.*; @RestController @RequestMapping("/users") public class UserController { /** Fetch user. */ @GetMapping("/{id}") public Envelope<User> get(@PathVariable("id") String id) { return null; } }',
    }
    for name, content in files.items():
        destination = project / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content)
    settings = root / "settings.xml"
    settings.write_text(f'<settings><localRepository>{root / "repository"}</localRepository><mirrors><mirror><id>smoke-central</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>')
    environment = {k: v for k, v in os.environ.items() if not k.startswith("EASYYAPI_")}
    environment["EASYYAPI_STATE_HOME"] = str(root / "state")
    timings = {}

    def run(name, *extra, succeeds=True, env=None):
        output = root / (name + ".json")
        started = time.monotonic()
        result = subprocess.run([
            str(launcher), "--project", str(project), "--maven-settings", str(settings),
            "--output", str(output), "--timeout", "180", *extra,
        ], env=env or environment, capture_output=True, timeout=210)
        timings[name] = round(time.monotonic() - started, 2)
        (root / (name + ".stdout")).write_bytes(result.stdout)
        (root / (name + ".stderr")).write_bytes(result.stderr)
        if succeeds:
            assert result.returncode == 0, f"{name} failed: {result.stderr.decode()[-3000:]}"
            return json.loads(output.read_text())
        assert result.returncode != 0, f"{name} unexpectedly succeeded"
        assert not output.exists(), f"{name} left a success artifact"
        return result.stderr.decode()

    def user_properties(document):
        operation = document["paths"]["/users/{id}"]["get"]
        parameter = operation["parameters"][0]
        assert parameter["name"] == "id" and parameter["required"]
        schema = operation["responses"]["200"]["content"]["application/json"]["schema"]
        schema = document["components"]["schemas"][schema["$ref"].split("/")[-1]]
        return schema["properties"]["data"]["properties"]

    print(f"Smoke artifacts: {root}", flush=True)
    assert not (project / ".idea").exists()
    cold = user_properties(run("cold"))
    assert cold["name"]["type"] == "string" and cold["age"]["type"] == "integer"
    source = project / "model/src/main/java/demo/User.java"
    source.write_text(source.read_text().replace("public int age;", "public int age; public String email;"))
    warm = user_properties(run("warm", "--class", "demo.UserController"))
    assert warm["email"]["type"] == "string", "External source changes were not reflected"
    error = run("missing-class", "--class", "demo.DoesNotExist", succeeds=False)
    assert "Class not found" in error
    error = run("missing-server", "--channel", "yapi", succeeds=False)
    assert "YAPI server URL is not configured" in error

    uploaded = []

    class YapiStub(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Test server intentionally avoids logging its dummy credential.

        def respond(self, data):
            payload = json.dumps({"errcode": 0, "data": data}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def do_GET(self):
            path = urlparse(self.path).path
            if path == "/api/project/get":
                self.respond({"_id": 1})
            elif path == "/api/interface/getCatMenu":
                self.respond([])
            elif path == "/api/interface/list_cat":
                self.respond({"list": [], "total": 0})
            else:
                self.send_error(404)

        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if self.path == "/api/interface/add_cat":
                self.respond({"_id": 2, "name": payload["name"]})
            elif self.path == "/api/interface/save":
                uploaded.append(payload)
                self.respond({"_id": 3})
            else:
                self.send_error(404)

    server = ThreadingHTTPServer(("127.0.0.1", 0), YapiStub)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    try:
        env = dict(environment, EASYYAPI_YAPI_SERVER=f"http://127.0.0.1:{server.server_port}", EASYYAPI_YAPI_TOKEN="smoke-token")
        result = run("yapi", "--channel", "yapi", env=env)
        assert result["count"] == 1 and len(uploaded) == 1
        assert uploaded[0]["path"] == "/users/{id}" and uploaded[0]["method"].upper() == "GET"
        assert "email" in uploaded[0]["res_body"]
        (root / "uploaded-api.json").write_text(json.dumps({k: v for k, v in uploaded[0].items() if k != "token"}, indent=2))
        # The standalone adapter must not persist environment credentials.
        for file in (root / "state/config").rglob("*.xml"):
            assert "smoke-token" not in file.read_text()
    finally:
        server.shutdown()
        server.server_close()
        worker.join()
    (root / "verification.json").write_text(json.dumps({"passed": True, "seconds": timings}, indent=2))
    print(json.dumps({"passed": True, "seconds": timings, "artifacts": str(root)}, indent=2))


if __name__ == "__main__":
    main()
