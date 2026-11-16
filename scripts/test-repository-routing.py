"""Exercise Gradle resolution against an unavailable Forge repository."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
from pathlib import Path
import subprocess
import sys
import threading
import uuid
import zipfile


def run_probe(args, order):
    source = Path(__file__).resolve().parents[1]
    work = args.work_dir.resolve() / str(uuid.uuid4())
    work.mkdir(parents=True)
    dependencies = [
        ('libraries', 'com.paulscode', 'codecjorbis', '20101023'),
        ('fabric', 'net.fabricmc', 'tiny-remapper', '0.11.2'),
        ('forge', 'net.minecraftforge', 'routing-fixture', '1.0'),
        ('forge', 'de.oceanlabs.mcp', 'routing-fixture', '1.0'),
    ]
    artifacts = {}
    jar_buffer = io.BytesIO()
    with zipfile.ZipFile(jar_buffer, 'w') as jar:
        jar.writestr('fixture.txt', 'repository-routing-regression')
    for repository, group, artifact, version in dependencies:
        prefix = f'/{repository}/{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}'
        artifacts[prefix + '.pom'] = (
            f'<project><modelVersion>4.0.0</modelVersion><groupId>{group}</groupId>'
            f'<artifactId>{artifact}</artifactId><version>{version}</version></project>'
        ).encode('utf-8')
        artifacts[prefix + '.jar'] = jar_buffer.getvalue()
    requests = []

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.respond(False)

        def do_HEAD(self):
            self.respond(True)

        def respond(self, head_only):
            requests.append(self.path)
            if self.path.startswith('/forge/') and not self.path.startswith(
                ('/forge/net/minecraftforge/', '/forge/de/oceanlabs/mcp/')
            ):
                self.send_error(503, 'Forge is unavailable for unrelated artifacts')
                return
            body = artifacts.get(self.path)
            if body is None:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            if not head_only:
                self.wfile.write(body)

        def log_message(self, *_args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    address = f'http://127.0.0.1:{server.server_port}'
    policy = source / 'gradle/scripts/repository-routing.gradle'
    settings = "rootProject.name = 'repository-routing-regression'\n"
    (work / 'settings.gradle').write_text(settings, encoding='utf-8')
    build = ''
    if policy.is_file() and order == 'future':
        build += f"apply from: '{policy.as_posix()}'\n"
    build += f'''repositories {{
    maven {{ name = 'minecraftForge'; url = '{address}/forge'; allowInsecureProtocol = true }}
    maven {{ name = 'minecraftLibraries'; url = '{address}/libraries'; allowInsecureProtocol = true }}
    maven {{ name = 'fabric'; url = '{address}/fabric'; allowInsecureProtocol = true }}
}}
'''
    if policy.is_file() and order == 'existing':
        build += f"apply from: '{policy.as_posix()}'\n"
    build += 'configurations { routingProbe }\ndependencies {\n'
    for _, group, artifact, version in dependencies:
        build += f"    routingProbe '{group}:{artifact}:{version}'\n"
    build += '''}
tasks.register('verifyRouting') {
    doLast {
        assert configurations.routingProbe.files.size() == 4
        println 'All four repository routing fixtures resolved'
    }
}
'''
    (work / 'build.gradle').write_text(build, encoding='utf-8')
    gradle = args.gradle or source / 'gradlew.bat'
    command = [str(gradle), '-p', str(work), '--no-daemon', '--console=plain', 'verifyRouting', '--stacktrace']
    try:
        result = subprocess.run(command, cwd=source, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    finally:
        server.shutdown()
        server.server_close()
        thread.join()
    (work / 'gradle.log').write_bytes(result.stdout)
    (work / 'requests.json').write_text(json.dumps(requests, indent=2), encoding='utf-8')
    if result.returncode:
        print(result.stdout.decode('utf-8', errors='replace')[-12000:])
    wrong_forge = [path for path in requests if path.startswith('/forge/') and not path.startswith(
        ('/forge/net/minecraftforge/', '/forge/de/oceanlabs/mcp/')
    )]
    assert not wrong_forge, f'Unrelated dependencies reached unavailable Forge: {wrong_forge}; evidence: {work}'
    assert result.returncode == 0, f'Gradle failed; see {work / "gradle.log"}'
    for prefix in ['/libraries/com/paulscode/', '/fabric/net/fabricmc/', '/forge/net/minecraftforge/', '/forge/de/oceanlabs/mcp/']:
        assert any(path.startswith(prefix) and path.endswith('.jar') for path in requests), prefix
    print(json.dumps({'passed': True, 'repositoryOrder': order, 'dependencies': 4, 'wrongForgeRequests': 0, 'evidence': str(work)}, ensure_ascii=True))


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='backslashreplace')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--gradle', type=Path)
    args = parser.parse_args()
    for order in ['existing', 'future']:
        run_probe(args, order)


if __name__ == '__main__':
    main()
