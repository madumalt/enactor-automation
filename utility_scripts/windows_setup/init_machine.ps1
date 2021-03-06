<powershell>
   $admin = [adsi]("WinNT://./administrator, user")
   $admin.PSBase.Invoke("SetPassword", "${password}")
   $script = ".\ConfigureMachineForAnsible.ps1"
   Invoke-Expression "$script"
</powershell>