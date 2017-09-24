package com.speedyblur.kretaremastered.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.speedyblur.kretaremastered.R;
import com.speedyblur.kretaremastered.models.Grade;
import com.speedyblur.kretaremastered.models.SubjectGradeGroup;
import com.speedyblur.kretaremastered.shared.Common;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class SubjectExpandableGradeAdapter extends BaseExpandableListAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final ArrayList<SubjectGradeGroup> subjectGradeGroups;

    public SubjectExpandableGradeAdapter(Context context, ArrayList<SubjectGradeGroup> subjectGradeGroups) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.subjectGradeGroups = subjectGradeGroups;
    }

    @Override
    public int getGroupCount() {
        return subjectGradeGroups.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return subjectGradeGroups.get(i).getGrades().size();
    }

    @Override
    public SubjectGradeGroup getGroup(int i) {
        return subjectGradeGroups.get(i);
    }

    @Override
    public Grade getChild(int i, int i1) {
        return subjectGradeGroups.get(i).getGrades().get(i1);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return false;
    }

    // TODO: ViewHolder pattern
    @Override
    @SuppressLint("inflateparams")
    public View getGroupView(int i, boolean isExp, View convertView, ViewGroup viewGroup) {
        convertView = inflater.inflate(R.layout.gradegroup_item, null);

        SubjectGradeGroup subjectGradeGroup = getGroup(i);
        View gradeGroupBar = convertView.findViewById(R.id.gradeGroupBar);
        TextView subjNameView = convertView.findViewById(R.id.gradeGroupTitle);

        gradeGroupBar.getLayoutParams().width = (int) Math.round(Resources.getSystem().getDisplayMetrics().widthPixels * 0.8);
        if (isExp) {
            Drawable ggbBg = ContextCompat.getDrawable(context, R.drawable.grade_group_bar);
            ggbBg.setColorFilter(ContextCompat.getColor(context, R.color.colorAccent), PorterDuff.Mode.SRC_ATOP);
            gradeGroupBar.setBackground(ggbBg);
        }

        subjNameView.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/OpenSans-Light.ttf"));
        subjNameView.setText(Common.getLocalizedSubjectName(context, subjectGradeGroup.getSubject()));

        return convertView;
    }

    @Override
    @SuppressLint("inflateparams")
    public View getChildView(int i, int i1, boolean isLastChild, View convertView, ViewGroup viewGroup) {
        Grade gradeObj = getChild(i, i1);

        if (gradeObj.getType().contains("végi") || gradeObj.getType().contains("Félévi")) {
            convertView = inflater.inflate(R.layout.gradelist_importantitem, null);
        } else {
            convertView = inflater.inflate(R.layout.gradelist_item, null);
        }

        // Common things
        TextView gradeView = convertView.findViewById(R.id.grade);
        TextView titleView = convertView.findViewById(R.id.gradeTitle);

        gradeView.setText(String.valueOf(gradeObj.getGrade()));

        // Not common things
        if (gradeObj.getType().contains("Félévi") || gradeObj.getType().contains("végi")) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(ContextCompat.getColor(context, gradeObj.getColorId()));
            gd.setCornerRadii(new float[] {200f, 200f, 0f, 0f, 0f, 0f, 200f, 200f});
            convertView.findViewById(R.id.importantGradeInnerLayout).setBackground(gd);
        }
        if (gradeObj.getType().contains("Félévi")) {
            titleView.setText(R.string.grade_end_of_halfterm);
        } else if (gradeObj.getType().contains("végi")) {
            titleView.setText(R.string.grade_end_of_year);
        } else {
            ImageView gradeBullet = convertView.findViewById(R.id.gradeBullet);
            gradeBullet.setColorFilter(ContextCompat.getColor(context, gradeObj.getColorId()), PorterDuff.Mode.SRC_ATOP);

            titleView.setText(capitalize(gradeObj.getType()));

            TextView descView1 = convertView.findViewById(R.id.gradeDesc);
            TextView descView2 = convertView.findViewById(R.id.gradeDesc2);

            if (gradeObj.getTheme().equals(" - ")) {
                descView1.setText(gradeObj.getTeacher());
            } else {
                descView1.setText(capitalize(gradeObj.getTheme()) + " - " + gradeObj.getTeacher());
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy. M. d.", Locale.getDefault());
            descView2.setText(dateFormat.format(new Date((long) gradeObj.getDate()*1000)));
        }

        return convertView;
    }

    /**
     *  Helper function to capitalize first letter of string
     */
    private String capitalize(final String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }
}
