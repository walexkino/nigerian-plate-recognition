"""Build PlateLab's local Android test APK using installed or project-local tools."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import hashlib
import json


def main():
    root=Path(__file__).resolve().parents[3]
    parser=argparse.ArgumentParser()
    parser.add_argument('--sdk',type=Path,help='Android SDK directory')
    parser.add_argument('--java-home',type=Path,help='JDK 17 or newer')
    parser.add_argument('--check',action='store_true',help='Run unit tests and lint as well as building')
    args=parser.parse_args()
    tools=root/'build/android-tools'
    sdk=args.sdk or (Path(os.environ['ANDROID_HOME']) if os.environ.get('ANDROID_HOME') else (tools/'compile-sdk' if (tools/'compile-sdk/platforms/android-36/android.jar').exists() else tools/'sdk'))
    java=args.java_home or (Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else next(tools.glob('jdk-*/Contents/Home'),None))
    if not java or not (java/'bin/java').is_file():
        raise SystemExit('JDK missing: install JDK 17 or newer and set JAVA_HOME, or use --java-home.')
    if not (sdk/'platforms/android-36/android.jar').is_file():
        raise SystemExit('Android SDK platform 36 missing. Install it after accepting the SDK licence; set ANDROID_HOME or use --sdk.')
    env=os.environ.copy()
    env.update(JAVA_HOME=str(java.resolve()),ANDROID_HOME=str(sdk.resolve()),ANDROID_USER_HOME=str(tools/'android-home'),GRADLE_USER_HOME=str(tools/'gradle-home'))
    tasks=['testDebugUnitTest','lintDebug','assembleDebug'] if args.check else ['assembleDebug']
    gradle=tools/'gradle-8.13/bin/gradle'
    command=str(gradle) if gradle.is_file() else str(root/'apps/android/gradlew')
    subprocess.run([command,*tasks,'--no-daemon','--console=plain'],cwd=root/'apps/android',env=env,check=True)
    source=root/'apps/android/app/build/outputs/apk/debug/app-debug.apk'
    out=root/'dist/android';out.mkdir(parents=True,exist_ok=True)
    metadata=json.loads((source.parent/'output-metadata.json').read_text())
    version=metadata['elements'][0]['versionName']
    target=out/('PlateLab-Android-'+version+'.apk')
    shutil.copy2(source,target)
    checksum=hashlib.sha256(target.read_bytes()).hexdigest()
    (out/(target.name+'.sha256')).write_text(checksum+'  '+target.name+'\n')
    print('APK:',target)
    print('SHA-256:',checksum)
    print('Local test APK signed with a development key. No store upload performed.')


if __name__=='__main__':
    main()
