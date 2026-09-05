#!/usr/bin/env python3
"""Build the version-pinned Java/Maven headless distribution (macOS/Linux).

Python is a build-time dependency only. The resulting launcher uses its bundled JBR.
Platform core JARs stay intact; optional plugin directories are selected explicitly.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import zipfile


PLUGINS = (
    "java", "maven", "properties", "repository-search", "java-decompiler",
    "platform-langInjection", "platform-ijent-impl", "java-ide-customization", "json",
)


def package(platform, plugins, output):
    platform = platform.resolve()
    info_file = next((p for p in (platform / "product-info.json", platform / "Resources/product-info.json") if p.is_file()), None)
    if info_file is None:
        raise ValueError("Platform directory must contain product-info.json or Resources/product-info.json")
    info = json.loads(info_file.read_text())
    if info["version"] != "2025.2.1":
        raise ValueError("This standalone adapter is pinned to IntelliJ Platform 2025.2.1")
    launch = info["launch"][0]
    if launch["os"] not in ("macOS", "Linux"):
        raise ValueError("The initial standalone launcher supports macOS and Linux")
    if output.exists():
        raise ValueError(f"Destination already exists; choose a fresh directory: {output}")
    for name in PLUGINS:
        if not (platform / "plugins" / name).is_dir():
            raise ValueError(f"Missing required runtime plugin: {name}")
    java = (info_file.parent / launch["javaExecutablePath"]).resolve()
    if not java.is_file():
        raise ValueError("Platform distribution must include JetBrains Runtime")
    output.mkdir(parents=True)
    runtime = output / "runtime"
    runtime.mkdir()
    for name in ("lib", "bin", "modules", "jbr", "license", "Resources"):
        source = platform / name
        if source.is_dir():
            shutil.copytree(source, runtime / name, symlinks=True)
    for name in ("product-info.json", "build.txt", "LICENSE.txt", "NOTICE.txt"):
        if (platform / name).is_file():
            shutil.copy2(platform / name, runtime / name)
    for name in PLUGINS:
        shutil.copytree(platform / "plugins" / name, runtime / "plugins" / name, symlinks=True)
    for plugin in plugins:
        with zipfile.ZipFile(plugin) as archive:
            for name in archive.namelist():
                if Path(name).is_absolute() or ".." in Path(name).parts:
                    raise ValueError("Unsafe plugin archive member")
            archive.extractall(output / "plugins")
    relative_java = java.relative_to(platform).as_posix()
    arguments = []
    for arg in launch["additionalJvmArguments"]:
        if arg.startswith(("-Didea.paths.selector=", "-Dsplash=", "-Didea.vendor.name=")):
            continue
        arg = arg.replace("$APP_PACKAGE/Contents", "${RUNTIME}").replace("$IDE_HOME", "${RUNTIME}")
        if "${RUNTIME}" in arg:
            arguments.append('"' + arg + '"')
        else:
            arguments.append(shlex.quote(arg))
    classpath = ":".join("${RUNTIME}/lib/" + name for name in launch["bootClassPathJarNames"])
    launcher = output / "bin/easyyapi"
    launcher.parent.mkdir()
    launcher.write_text("\n".join([
        "#!/bin/sh", "set -eu",
        'if [ "${1:-}" = "--help" ] && [ "$#" -eq 1 ]; then',
        "  printf '%s\\n' 'easyyapi --project <Maven directory> --output <file> [--jdk <JDK home>] [--channel openapi|yapi] [--format json|yaml] [--class <qualified.name>] [--timeout <seconds>] [--maven-settings <settings.xml>]'",
        "  exit 0", "fi",
        'ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)',
        'RUNTIME="$ROOT/runtime"',
        'STATE=${EASYYAPI_STATE_HOME:-"$HOME/.easyyapi/standalone-252"}',
        'mkdir -p "$STATE/config" "$STATE/system" "$STATE/log"',
        'exec "$RUNTIME/' + relative_java + '" -Xmx${EASYYAPI_MAX_HEAP:-2048m} ' + " ".join(arguments) + " \\",
        '  -Djava.awt.headless=true -Didea.is.command.line=true -Didea.headless.enable=true \\',
        '  -Didea.home.path="$RUNTIME" -Didea.config.path="$STATE/config" \\',
        '  -Didea.system.path="$STATE/system" -Didea.log.path="$STATE/log" \\',
        '  -Didea.plugins.path="$ROOT/plugins" -Didea.auto.reload.plugins=false \\',
        '  -cp "' + classpath + '" ' + launch.get("mainClass", "com.intellij.idea.Main") + ' easyyapi "$@"',
        "",
    ]))
    launcher.chmod(0o755)
    (output / "runtime-manifest.json").write_text(json.dumps({
        "platformVersion": info["version"], "buildNumber": info["buildNumber"],
        "os": launch["os"], "arch": launch["arch"], "plugins": PLUGINS,
        "pluginArchives": [plugin.name for plugin in plugins],
        "scope": "Java/Maven; core platform JARs retained, optional language/UI plugins excluded",
    }, indent=2) + "\n")
    shutil.copy2(Path(__file__).resolve().parents[1] / "docs/developer/standalone.md", output / "README.md")
    print(launcher)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True, type=Path)
    parser.add_argument("--plugin", required=True, type=Path, action="append")
    parser.add_argument("--output", required=True, type=Path)
    options = parser.parse_args()
    package(options.platform, options.plugin, options.output)
