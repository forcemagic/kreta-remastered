package com.speedyblur.kretaremastered.models;

public class Profile {
    private final String cardid;
    private final String passwd;
    private final String friendlyName;

    public Profile(String cardid, String passwd, String friendlyName) {
        this.cardid = cardid;
        this.passwd = passwd;
        this.friendlyName = friendlyName;
    }

    public boolean hasFriendlyName() {
        return !this.friendlyName.equals("");
    }

    // Getter methods
    public String getCardid() {
        return cardid;
    }
    public String getPasswd() {
        return this.passwd;
    }
    public String getFriendlyName() {
        return friendlyName;
    }
}
