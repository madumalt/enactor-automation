<powershell>
    $admin = [adsi]("WinNT://./administrator, user")
    $admin.PSBase.Invoke("SetPassword", "${password}")
    Invoke-Expression ((New-Object System.Net.Webclient).DownloadString('https://raw.githubusercontent.com/ansible/ansible/devel/examples/scripts/ConfigureRemotingForAnsible.ps1'))
</powershell>