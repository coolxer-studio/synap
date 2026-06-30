package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosFileFact
import org.apache.commons.lang3.StringUtils

def rootWithFileExist(IosFileFact fact){
    ArrayList<String> rootForExist = Arrays.asList(
            "/Applications/Cydia.app",
            "/var/lib/cydia",
            "/var/mobile/Library/Cydia",
            "/usr/libexec/cydia",
            "/var/cache/apt",
            "/var/lib/apt",
            "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/bin/bash",
            "/bin/sh",
            "/usr/sbin/sshd",
            "/usr/libexec/ssh-keysign",
            "/etc/apt",
            "/etc/ssh/sshd_config",
            "/Library/MobileSubstrate/DynamicLibraries/xCon.plist",
            "/Library/MobileSubstrate/DynamicLibraries/xCon.dylib",
            "/Library/MobileSubstrate/DynamicLibraries/OTRLocation.dylib",
            "/usr/sbin/frida-server", // frida
            "/etc/apt/sources.list.d/electra.list", // electra
            "/etc/apt/sources.list.d/sileo.sources", // electra
            "/.bootstrapped_electra", // electra
            "/usr/lib/libjailbreak.dylib", // electra
            "/jb/lzma", // electra
            "/.cydia_no_stash", // unc0ver
            "/.installed_unc0ver", // unc0ver
            "/jb/offsets.plist", // unc0ver
            "/usr/share/jailbreak/injectme.plist", // unc0ver
            "/etc/apt/undecimus/undecimus.list", // unc0ver
            "/var/lib/dpkg/info/mobilesubstrate.md5sums", // unc0ver
            "/jb/jailbreakd.plist", // unc0ver
            "/jb/amfid_payload.dylib", // unc0ver
            "/jb/libjailbreak.dylib", // unc0ver
            "/usr/libexec/cydia/firmware.sh",
            "/private/var/lib/apt",
            "/private/var/Users/",
            "/var/log/apt",
            "/private/var/stash",
            "/private/var/lib/apt/",
            "/private/var/lib/cydia",
            "/private/var/cache/apt/",
            "/private/var/log/syslog",
            "/private/var/tmp/cydia.log",
            "/Applications/Icy.app",
            "/Applications/MxTube.app",
            "/Applications/RockApp.app",
            "/Applications/blackra1n.app",
            "/Applications/SBSettings.app",
            "/Applications/FakeCarrier.app",
            "/Applications/WinterBoard.app",
            "/Applications/IntelliScreen.app",
            "/private/var/mobile/Library/SBSettings/Themes",
            "/Library/MobileSubstrate/CydiaSubstrate.dylib",
            "/System/Library/LaunchDaemons/com.ikey.bbot.plist",
            "/Library/MobileSubstrate/DynamicLibraries/Veency.plist",
            "/Library/MobileSubstrate/DynamicLibraries/LiveClock.plist",
            "/System/Library/LaunchDaemons/com.saurik.Cydia.Startup.plist",
            "/Applications/Sileo.app",
            "/var/binpack",
            "/Library/PreferenceBundles/LibertyPref.bundle",
            "/Library/PreferenceBundles/ShadowPreferences.bundle",
            "/Library/PreferenceBundles/ABypassPrefs.bundle",
            "/Library/PreferenceBundles/FlyJBPrefs.bundle",
            "/usr/lib/libhooker.dylib",
            "/usr/lib/libsubstitute.dylib",
            "/usr/lib/substrate",
            "/usr/lib/TweakInject",
            "/var/binpack/Applications/loader.app", // checkra1n
            "/Applications/FlyJB.app", // Fly JB X
            "/Applications/Zebra.app" // Zebra
    )
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String path: rootForExist){
        if(fact.getExist().contains(path)){
            agendaList.add(new Agenda("ROOT",path));
        }
    }
    return agendaList
}

def rootWithFileLink(IosFileFact fact){
    ArrayList<String> lnfile = Arrays.asList("/Applications", "/usr/share",
            "/usr/libexec", "/usr/include", "/Library/Ringtones");
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String path: lnfile){
        if(StringUtils.isNotEmpty(fact.getLinkFiles().get(path))){
            agendaList.add(new Agenda("ROOT",path+":"+fact.getLinkFiles().get(path), AgendaLevelEnum.HIGH));
        }
    }
    return agendaList
}

def rootWithFileAccess(IosFileFact fact){
    ArrayList<String> rootPath = Arrays.asList(
            "/",
            "/root/",
            "/private/",
            "/jb/")
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String path: rootPath){
        if(fact.getAccess().get(path,-1) > 1){
            agendaList.add(new Agenda("ROOT",path+":"+fact.getAccess().get(path,-1),AgendaLevelEnum.HIGH));
        }
    }
    return agendaList
}

def frameForFridaWithFileList(IosFileFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String filenName: fact.getFileList()){
        if(filenName.contains("frida-")){
            agendaList.add(new Agenda("frida",filenName,AgendaLevelEnum.HIGH));
        }
    }
    return agendaList
}

def frameForFridaWithFileExist(IosFileFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String filenName: fact.getExist()){
        if(filenName.contains("frida-server")){
            agendaList.add(new Agenda("frida",filenName,AgendaLevelEnum.HIGH));
        }
    }
    return agendaList
}

def frameForCydiaWithFileExist(IosFileFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String filenName: fact.getExist()){
        if(filenName.contains("cydia")){
            agendaList.add(new Agenda("cydia",filenName,AgendaLevelEnum.MEDIUM));
        }
    }
    return agendaList
}

def frameForCycriptWithFileExist(IosFileFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String filenName: fact.getExist()){
        if(filenName.contains("/usr/bin/cycript")){
            agendaList.add(new Agenda("cycript",filenName,AgendaLevelEnum.MEDIUM));
        }
    }
    return agendaList
}

ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.addAll(rootWithFileExist(fact))
agendaList.addAll(rootWithFileLink(fact))
agendaList.addAll(rootWithFileAccess(fact))
agendaList.addAll(frameForFridaWithFileList(fact))
agendaList.addAll(frameForFridaWithFileExist(fact))
agendaList.addAll(frameForCydiaWithFileExist(fact))
agendaList.addAll(frameForCycriptWithFileExist(fact))
return agendaList