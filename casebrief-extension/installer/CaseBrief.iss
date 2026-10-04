#define AppVersion "1.0.0"
#define ExtensionId "kgblcnakcckleijbjknldipphmkficma"

[Setup]
AppId={{DA93C905-BC2A-457C-B6A1-EAA3F087B56A}
AppName=CaseBrief Native Host
AppVersion={#AppVersion}
DefaultDirName={localappdata}\Programs\CaseBrief\NativeHost
DefaultGroupName=CaseBrief
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir=..\..\dist
OutputBaseFilename=CaseBriefNativeHostSetup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern

[Files]
Source: "..\..\target\native-host-dist\CaseBriefNativeHost\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Code]
const
  NativeHostName = 'com.casebrief.native';
  ChromeHostRegistryKey = 'Software\Google\Chrome\NativeMessagingHosts\com.casebrief.native';

procedure CurStepChanged(CurStep: TSetupStep);
var
  HostExecutable: string;
  EscapedHostExecutable: string;
  ManifestPath: string;
  ManifestText: string;
begin
  if CurStep = ssPostInstall then
  begin
    HostExecutable := ExpandConstant('{app}\CaseBriefNativeHost.exe');
    if not FileExists(HostExecutable) then
      RaiseException('CaseBriefNativeHost.exe was not installed.');

    EscapedHostExecutable := HostExecutable;
    StringChangeEx(EscapedHostExecutable, '\', '\\', True);
    ManifestPath := ExpandConstant('{app}\com.casebrief.native.json');
    ManifestText := '{' +
      '"name":"' + NativeHostName + '",' +
      '"description":"CaseBrief local document processing host",' +
      '"path":"' + EscapedHostExecutable + '",' +
      '"type":"stdio",' +
      '"allowed_origins":["chrome-extension://{#ExtensionId}/"]' +
      '}' + #13#10;

    if not SaveStringToFile(ManifestPath, ManifestText, False) then
      RaiseException('Could not write the Chrome Native Messaging manifest.');
    if not RegWriteStringValue(HKCU, ChromeHostRegistryKey, '', ManifestPath) then
      RaiseException('Could not register the CaseBrief Native Messaging host.');
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    RegDeleteKeyIncludingSubkeys(HKCU, ChromeHostRegistryKey);
    DeleteFile(ExpandConstant('{app}\com.casebrief.native.json'));
  end;
end;
