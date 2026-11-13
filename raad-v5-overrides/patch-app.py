"""Patch the RAAD v4 HTML frontend during reproducible GitHub APK packaging.
Only navigation actions bypass the double confirmation. All sensitive actions retain approval.
"""
from pathlib import Path
p=Path('src/app.js')
s=p.read_text()
old=' if(!confirm(`RAAD wants to prepare this Android action. Review before proceeding:\\n\\n${label}\\n\\nThe target app still requires your final confirmation.`))return false;'
new=" if(!['media','web-search','open_app'].includes(validated.type) && !confirm(`RAAD wants to prepare this Android action. Review before proceeding:\\n\\n${label}\\n\\nThe target app still requires your final confirmation.`))return false;"
if old not in s and new not in s:raise SystemExit('RAAD app action review hook changed; refusing blind patch')
if old in s:s=s.replace(old,new,1)
old='`Prepared ${direct.type} in Android. ${DEVICE_COPY[direct.type]}`'
new='`${["media","web-search","open_app"].includes(direct.type)?"Requested":"Prepared"} ${direct.type} in Android. ${DEVICE_COPY[direct.type]}`'
if old in s:s=s.replace(old,new,1)
p.write_text(s)
print('RAAD Android navigation actions enabled without redundant confirmation')