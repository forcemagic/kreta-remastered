package com.speedyblur.kretaremastered;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.Utils;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.speedyblur.adapters.AbsenceAdapter;
import com.speedyblur.adapters.AverageAdapter;
import com.speedyblur.adapters.DatedGradeAdapter;
import com.speedyblur.adapters.GroupedGradeAdapter;
import com.speedyblur.models.Absence;
import com.speedyblur.models.AllDayEvent;
import com.speedyblur.models.Average;
import com.speedyblur.models.ClassEvent;
import com.speedyblur.models.Grade;
import com.speedyblur.models.GradeGroup;
import com.speedyblur.shared.HttpHandler;
import com.speedyblur.shared.Vars;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // In-memory "data stores"
    private ArrayMap<String, String> heads;
    private ArrayList<Grade> allGrades;
    private ArrayList<Average> averages;
    private ArrayList<Absence> absences;
    private ArrayList<AllDayEvent> allDayEvents;
    private ArrayList<ClassEvent> classes;

    private final Context sharedCtxt = this;
    private double loadTime;
    private boolean shouldShowMenu = true;
    private String lastMenuState;
    private CalendarDay lastAbsenceDate;

    // UI ref
    private Toolbar toolbar;
    private ViewFlipper vf;
    private ViewFlipper gVf;
    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_grades);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        //noinspection deprecation
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.mainNav);
        navigationView.setNavigationItemSelectedListener(this);

        // Set profile name to drawer header
        TextView profNameHead = navigationView.getHeaderView(0).findViewById(R.id.profileNameHead);
        profNameHead.setText(getIntent().getStringExtra("profileName"));

        vf = (ViewFlipper) findViewById(R.id.main_viewflipper);
        gVf = (ViewFlipper) findViewById(R.id.gradeOrderFlipper);

        // Absence list empty
        ListView absList = (ListView)findViewById(R.id.absenceList);
        absList.setEmptyView(findViewById(R.id.noAbsenceView));

        heads = new ArrayMap<>();
        heads.put("X-Auth-Token", Vars.AUTHTOKEN);

        if (savedInstanceState != null) {
            allGrades = savedInstanceState.getParcelableArrayList("allGrades");
            averages = savedInstanceState.getParcelableArrayList("averages");
            absences = savedInstanceState.getParcelableArrayList("absences");
            allDayEvents = savedInstanceState.getParcelableArrayList("allDayEvents");
            classes = savedInstanceState.getParcelableArrayList("classes");

            // Repopulate views
            ExpandableListView gradeList = (ExpandableListView) findViewById(R.id.mainGradeView);
            gradeList.setDividerHeight(0);
            gradeList.setAdapter(new GroupedGradeAdapter(sharedCtxt, allGrades, "subject", new GradeGroup.FormatHelper() {
                @Override
                public String doFormat(String in) {
                    return Vars.getLocalizedSubjectName(sharedCtxt, in);
                }
            }, new GradeGroup.SameGroupComparator() {
                @Override
                public boolean compare(String id1, String id2) {
                    return id1.equals(id2);
                }
            }));

            ListView avgList = (ListView) findViewById(R.id.avg_list);
            avgList.setAdapter(new AverageAdapter(sharedCtxt, averages));

            ListView dateOrderedLv = (ListView) findViewById(R.id.datedGradeList);
            try {
                dateOrderedLv.setAdapter(new DatedGradeAdapter(sharedCtxt, GradeGroup.assembleGroups(allGrades, "gotDate", new GradeGroup.SameGroupComparator() {
                    @Override
                    public boolean compare(String id1, String id2) {
                        Calendar cal1 = Calendar.getInstance();
                        Calendar cal2 = Calendar.getInstance();
                        cal1.setTimeInMillis(Long.parseLong(id1)*1000);
                        cal2.setTimeInMillis(Long.parseLong(id2)*1000);
                        return (cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH));
                    }
                }, false, true)));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            // Reset absence day
            showAbsenceListForDate((CalendarDay) savedInstanceState.getParcelable("lastAbsenceDate"));

            // Viewflipper reset
            vf.setDisplayedChild(savedInstanceState.getInt("viewFlipperState"));
            gVf.setDisplayedChild(savedInstanceState.getInt("gradeViewFlipperState"));
            shouldShowMenu = savedInstanceState.getBoolean("shouldShowMenu");
            if (shouldShowMenu) lastMenuState = savedInstanceState.getString("sortingTitle");
            toolbar.setTitle(savedInstanceState.getString("toolbarTitle"));
        } else {
            vf.setDisplayedChild(0);
            gVf.setDisplayedChild(0);
            fetchGrades();
        }
    }

    private void fetchGrades() {
        Snackbar.make(findViewById(R.id.main_coord_view), R.string.loading_grades, Snackbar.LENGTH_INDEFINITE).show();
        HttpHandler.getJson(Vars.APIBASE + "/grades", heads, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JSONObject resp) throws JSONException {
                allGrades = Grade.fromJson(resp.getJSONArray("data"));
                loadTime = resp.getDouble("fetch_time");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Subject-ordered
                        ExpandableListView lv = (ExpandableListView) findViewById(R.id.mainGradeView);
                        lv.setDividerHeight(0);
                        lv.setAdapter(new GroupedGradeAdapter(sharedCtxt, allGrades, "subject", new GradeGroup.FormatHelper() {
                            @Override
                            public String doFormat(String in) {
                                return Vars.getLocalizedSubjectName(sharedCtxt, in);
                            }
                        }, new GradeGroup.SameGroupComparator() {
                            @Override
                            public boolean compare(String id1, String id2) {
                                return id1.equals(id2);
                            }
                        }));

                        // Date-ordered
                        ListView dateOrderedLv = (ListView) findViewById(R.id.datedGradeList);
                        try {
                            dateOrderedLv.setAdapter(new DatedGradeAdapter(sharedCtxt, GradeGroup.assembleGroups(allGrades, "gotDate", new GradeGroup.SameGroupComparator() {
                                @Override
                                public boolean compare(String id1, String id2) {
                                    Calendar cal1 = Calendar.getInstance();
                                    Calendar cal2 = Calendar.getInstance();
                                    cal1.setTimeInMillis(Long.parseLong(id1)*1000);
                                    cal2.setTimeInMillis(Long.parseLong(id2)*1000);
                                    return (cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH));
                                }
                            }, false, true)));
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        fetchAverages();
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(findViewById(R.id.main_coord_view), localizedError, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void fetchAverages() {
        Snackbar.make(findViewById(R.id.main_coord_view), R.string.loading_averages, Snackbar.LENGTH_INDEFINITE).show();
        HttpHandler.getJson(Vars.APIBASE + "/avg", heads, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JSONObject resp) throws JSONException {
                averages = Average.fromJson(resp.getJSONArray("data"));
                loadTime += resp.getDouble("fetch_time");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ListView lv = (ListView) findViewById(R.id.avg_list);
                        lv.setAdapter(new AverageAdapter(sharedCtxt, averages));
                        fetchAverageGraph();
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(findViewById(R.id.main_coord_view), localizedError, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void fetchAverageGraph() {
        Snackbar.make(findViewById(R.id.main_coord_view), R.string.loading_absence, Snackbar.LENGTH_INDEFINITE).show();
        HttpHandler.getJson(Vars.APIBASE + "/avggraph", heads, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JSONObject resp) throws JSONException {
                Utils.init(getApplicationContext());
                JSONArray graphDataCollection = resp.getJSONArray("data");
                for (int i=0; i<graphDataCollection.length(); i++) {
                    JSONObject graphData = graphDataCollection.getJSONObject(i);
                    if (graphData.getJSONArray("points").length() == 0) continue;
                    List<Entry> graphDataEntries = new ArrayList<>();
                    for (int j=0; j<graphData.getJSONArray("points").length(); j++) {
                        JSONObject current = graphData.getJSONArray("points").getJSONObject(j);
                        graphDataEntries.add(new Entry((float)current.getInt("x"), (float)current.getDouble("y")));
                        if (current.getBoolean("isspecial") && !Vars.halfTermTimes.containsKey(graphData.getString("subject"))) {
                            Vars.halfTermTimes.put(graphData.getString("subject"), current.getInt("x"));
                        }
                    }
                    Vars.averageGraphData.put(graphData.getString("subject"), new LineDataSet(graphDataEntries, graphData.getString("subject")));
                }
                loadTime += resp.getDouble("fetch_time");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fetchAbsences();
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(findViewById(R.id.main_coord_view), localizedError, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void fetchAbsences() {
        HttpHandler.getJson(Vars.APIBASE + "/absence", heads, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JSONObject resp) throws JSONException {
                absences = Absence.fromJson(resp.getJSONArray("data"));
                loadTime += resp.getDouble("fetch_time");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showAbsenceListForDate(CalendarDay.from(new Date((long) absences.get(absences.size()-1).date*1000)));
                        fetchTimetable();
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(findViewById(R.id.main_coord_view), localizedError, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void fetchTimetable() {
        HttpHandler.getJson(Vars.APIBASE + "/schedule", heads, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JSONObject resp) throws JSONException {
                classes = ClassEvent.fromJson(resp.getJSONObject("data").getJSONArray("classes"));
                allDayEvents = AllDayEvent.fromJson(resp.getJSONObject("data").getJSONArray("allday"));
                loadTime += resp.getDouble("fetch_time");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(findViewById(R.id.main_coord_view), getResources().getString(R.string.main_load_complete, (float)loadTime), Snackbar.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Snackbar.make(findViewById(R.id.main_coord_view), localizedError, Snackbar.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showAbsenceListForDate(CalendarDay day) {
        lastAbsenceDate = day;

        Calendar c = day.getCalendar();
        ArrayList<Absence> listElements = new ArrayList<>();
        for (int i=0; i<absences.size(); i++) {
            Calendar toCompare = Calendar.getInstance();
            toCompare.setTimeInMillis((long)absences.get(i).date*1000);
            if (toCompare.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) listElements.add(absences.get(i));
        }
        Collections.sort(listElements, new Comparator<Absence>() {
            @Override
            public int compare(Absence t1, Absence t2) {
                return t1.classNum - t2.classNum;
            }
        });

        // "Select" dayofweek
        // TODO: Optimize code! There are things like these in bulletSelectWeekday(...)
        findViewById(R.id.absenceMondaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceTuesdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceWednesdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceThursdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceFridaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceSaturdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        Drawable selectedBullet = ContextCompat.getDrawable(this, R.drawable.weekday_selector).mutate();
        selectedBullet.setColorFilter(ContextCompat.getColor(this, R.color.weekdayActive), PorterDuff.Mode.SRC_ATOP);
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            findViewById(R.id.absenceMondaySelector).setBackground(selectedBullet);
        } else if (c.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY) {
            findViewById(R.id.absenceTuesdaySelector).setBackground(selectedBullet);
        } else if (c.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY) {
            findViewById(R.id.absenceWednesdaySelector).setBackground(selectedBullet);
        } else if (c.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) {
            findViewById(R.id.absenceThursdaySelector).setBackground(selectedBullet);
        } else if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            findViewById(R.id.absenceFridaySelector).setBackground(selectedBullet);
        } else if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            findViewById(R.id.absenceSaturdaySelector).setBackground(selectedBullet);
        }

        ListView lv = (ListView)findViewById(R.id.absenceList);
        lv.setAdapter(new AbsenceAdapter(this, listElements));

        TextView currentDate = (TextView) findViewById(R.id.currentAbsenceListDate);
        currentDate.setText(new SimpleDateFormat("YYYY. MMMM dd.", Locale.getDefault()).format(c.getTime()));
    }

    public void openAbsenceCalendar(View v) {
        AlertDialog.Builder calDialog = new AlertDialog.Builder(sharedCtxt);

        // Calendar setup
        View inflView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar, null);
        final MaterialCalendarView cView = inflView.findViewById(R.id.absenceCalendar);
        final ArrayList<CalendarDay> provenDates = new ArrayList<>();
        final ArrayList<CalendarDay> unprovenDates = new ArrayList<>();
        for (int i=0; i<absences.size(); i++) {
            if (absences.get(i).proven) provenDates.add(CalendarDay.from(new Date((long) absences.get(i).date*1000)));
            else unprovenDates.add(CalendarDay.from(new Date((long) absences.get(i).date*1000)));
        }
        cView.addDecorators(new DayViewDecorator() {
            @Override
            public boolean shouldDecorate(CalendarDay day) {
                return provenDates.contains(day);
            }

            @Override
            public void decorate(DayViewFacade view) {
                view.setBackgroundDrawable(ContextCompat.getDrawable(sharedCtxt, R.drawable.calendar_goodbullet));
            }
        }, new DayViewDecorator() {
            @Override
            public boolean shouldDecorate(CalendarDay day) {
                return unprovenDates.contains(day);
            }

            @Override
            public void decorate(DayViewFacade view) {
                view.setBackgroundDrawable(ContextCompat.getDrawable(sharedCtxt, R.drawable.calendar_badbullet));
            }
        });

        calDialog.setView(inflView);
        calDialog.setTitle(R.string.select_date);
        calDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (cView.getSelectedDate() != null) showAbsenceListForDate(cView.getSelectedDate());
                dialogInterface.dismiss();
            }
        });
        calDialog.show();
    }

    public void bulletSelectWeekday(View v) {
        // Setting "selected" background
        findViewById(R.id.absenceMondaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceTuesdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceWednesdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceThursdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceFridaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        findViewById(R.id.absenceSaturdaySelector).setBackground(ContextCompat.getDrawable(this, R.drawable.weekday_selector));
        Drawable selectedBullet = ContextCompat.getDrawable(this, R.drawable.weekday_selector).mutate();
        selectedBullet.setColorFilter(ContextCompat.getColor(this, R.color.weekdayActive), PorterDuff.Mode.SRC_ATOP);
        v.setBackground(selectedBullet);

        Calendar c = lastAbsenceDate.getCalendar();
        if (v.getId() == R.id.absenceMondaySelector) {
            c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        } else if (v.getId() == R.id.absenceTuesdaySelector) {
            c.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        } else if (v.getId() == R.id.absenceWednesdaySelector) {
            c.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
        } else if (v.getId() == R.id.absenceThursdaySelector) {
            c.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
        } else if (v.getId() == R.id.absenceFridaySelector) {
            c.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
        } else if (v.getId() == R.id.absenceSaturdaySelector) {
            c.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        }
        showAbsenceListForDate(CalendarDay.from(c));
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        b.putParcelableArrayList("allGrades", allGrades);
        b.putParcelableArrayList("averages", averages);
        b.putParcelableArrayList("absences", absences);
        b.putParcelableArrayList("allDayEvents", allDayEvents);
        b.putParcelableArrayList("classes", classes);

        b.putInt("viewFlipperState", vf.getDisplayedChild());
        b.putInt("gradeViewFlipperState", gVf.getDisplayedChild());
        b.putBoolean("shouldShowMenu", shouldShowMenu);
        if (shouldShowMenu) b.putString("sortingTitle", menu.getItem(0).getTitle().toString());
        b.putString("toolbarTitle", toolbar.getTitle().toString());
        b.putParcelable("lastAbsenceDate", lastAbsenceDate);
        super.onSaveInstanceState(b);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem mChangeSort = menu.getItem(0);
        mChangeSort.setTitle(lastMenuState == null ? getResources().getString(R.string.action_sortbydate) : lastMenuState);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return shouldShowMenu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Currently this check is not necessary, but let's do it anyway
        if (item.getItemId() == R.id.action_changesort) {
            if (item.getTitle() == getResources().getString(R.string.action_sortbydate)) {
                gVf.setDisplayedChild(1);
                item.setTitle(R.string.action_sortbysubject);
            } else {
                gVf.setDisplayedChild(0);
                item.setTitle(R.string.action_sortbydate);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_avgs) {
            toolbar.setTitle(R.string.title_activity_avgs);
            shouldShowMenu = false;
            invalidateOptionsMenu();
            vf.setDisplayedChild(1);
        } else if (id == R.id.nav_grades) {
            toolbar.setTitle(R.string.title_activity_grades);
            getMenuInflater().inflate(R.menu.main, menu);
            shouldShowMenu = true;
            invalidateOptionsMenu();
            vf.setDisplayedChild(0);
            gVf.setDisplayedChild(0);
        } else if (id == R.id.nav_absences) {
            toolbar.setTitle(R.string.title_activity_absences);
            shouldShowMenu = false;
            invalidateOptionsMenu();
            vf.setDisplayedChild(2);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
