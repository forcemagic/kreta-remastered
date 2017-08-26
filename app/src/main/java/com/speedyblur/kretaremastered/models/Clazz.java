package com.speedyblur.kretaremastered.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

public class Clazz implements Parcelable {
    private String subject;
    private String group;
    private String teacher;
    private String room;
    private int classnum;
    private int beginTime;
    private int endTime;
    private String theme;
    private boolean isAbsent;
    private AbsenceDetails absenceDetails;

    public Clazz(String subject, String group, String teacher, String room, int classnum, int begin, int end, String theme, boolean isabsent,
                 @Nullable AbsenceDetails absenceDetails) {
        this.subject = subject;
        this.group = group;
        this.teacher = teacher;
        this.room = room;
        this.classnum = classnum;
        this.beginTime = begin;
        this.endTime = end;
        this.theme = theme;
        this.isAbsent = isabsent;
        this.absenceDetails = absenceDetails;
    }

    protected Clazz(Parcel in) {
        subject = in.readString();
        group = in.readString();
        teacher = in.readString();
        room = in.readString();
        classnum = in.readInt();
        beginTime = in.readInt();
        endTime = in.readInt();
        theme = in.readString();
    }

    public static final Creator<Clazz> CREATOR = new Creator<Clazz>() {
        @Override
        public Clazz createFromParcel(Parcel in) {
            return new Clazz(in);
        }

        @Override
        public Clazz[] newArray(int size) {
            return new Clazz[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(subject);
        parcel.writeString(group);
        parcel.writeString(teacher);
        parcel.writeString(room);
        parcel.writeInt(classnum);
        parcel.writeInt(beginTime);
        parcel.writeInt(endTime);
        parcel.writeString(theme);
    }

    // Getter methods
    public String getSubject() {
        return subject;
    }
    public String getGroup() {
        return group;
    }
    public String getTeacher() {
        return teacher;
    }
    public String getRoom() {
        return room;
    }
    public int getClassnum() {
        return classnum;
    }
    public int getBeginTime() {
        return beginTime;
    }
    public int getEndTime() {
        return endTime;
    }
    public String getTheme() {
        return theme;
    }
    public boolean isAbsent() {
        return isAbsent;
    }
    public AbsenceDetails getAbsenceDetails() {
        return absenceDetails;
    }
}
