package com.applikeysolutions.cosmocalendar.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.applikeysolutions.cosmocalendar.adapter.MonthAdapter;
import com.applikeysolutions.cosmocalendar.model.Day;
import com.applikeysolutions.cosmocalendar.model.Month;
import com.applikeysolutions.cosmocalendar.selection.BaseSelectionManager;
import com.applikeysolutions.cosmocalendar.selection.NoneSelectionManager;
import com.applikeysolutions.cosmocalendar.selection.OnDaySelectedListener;
import com.applikeysolutions.cosmocalendar.selection.RangeSelectionManager;
import com.applikeysolutions.cosmocalendar.selection.SingleSelectionManager;
import com.applikeysolutions.cosmocalendar.settings.SettingsManager;
import com.applikeysolutions.cosmocalendar.settings.appearance.AppearanceInterface;
import com.applikeysolutions.cosmocalendar.settings.date.DateInterface;
import com.applikeysolutions.cosmocalendar.settings.lists.CalendarListsInterface;
import com.applikeysolutions.cosmocalendar.settings.lists.DisabledDaysCriteria;
import com.applikeysolutions.cosmocalendar.settings.lists.connected_days.ConnectedDays;
import com.applikeysolutions.cosmocalendar.settings.lists.connected_days.ConnectedDaysManager;
import com.applikeysolutions.cosmocalendar.settings.selection.SelectionInterface;
import com.applikeysolutions.cosmocalendar.utils.CalendarUtils;
import com.applikeysolutions.cosmocalendar.utils.SelectionType;
import com.applikeysolutions.cosmocalendar.utils.WeekDay;
import com.applikeysolutions.cosmocalendar.view.customviews.CircleAnimationTextView;
import com.applikeysolutions.cosmocalendar.view.customviews.SquareTextView;
import com.applikeysolutions.cosmocalendar.view.delegate.MonthDelegate;
import com.applikeysolutions.customizablecalendar.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CalendarView extends RelativeLayout implements OnDaySelectedListener,
        AppearanceInterface, DateInterface, CalendarListsInterface, SelectionInterface {

    private List<Day> selectedDays;

    //Recycler
    private SlowdownRecyclerView rvMonths;
    private MonthAdapter monthAdapter;

    //Bottom selection bar
    private FrameLayout flBottomSelectionBar;
    //Range mode
    private LinearLayout llRangeSelection;

    //Views
    private LinearLayout llDaysOfWeekTitles;
    private FrameLayout flNavigationButtons;
    private ImageView ivPrevious;
    private ImageView ivNext;

    //Helpers
    private SettingsManager settingsManager;
    private BaseSelectionManager selectionManager;

    private int lastVisibleMonthPosition = 0;

    private DateSelectionListener listener;
    /**
     * Scroll listener for month pagination
     */
    private RecyclerView.OnScrollListener pagingScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            //Fix for bug with bottom selection bar and different month item height in horizontal mode (different count of weeks)
            View view = rvMonths.getLayoutManager().findViewByPosition(getFirstVisiblePosition(rvMonths.getLayoutManager()));
            if (view != null) {
                view.requestLayout();
            }

            if (getCalendarOrientation() == OrientationHelper.HORIZONTAL) {
                //Hide navigation buttons
                boolean show = newState != RecyclerView.SCROLL_STATE_DRAGGING;
                ivPrevious.setVisibility(show ? View.VISIBLE : View.GONE);
                ivNext.setVisibility(show ? View.VISIBLE : View.GONE);
            }

            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            final RecyclerView.LayoutManager manager = rvMonths.getLayoutManager();

            lastVisibleMonthPosition = getFirstVisiblePosition(manager);
        }
    };

    public CalendarView(Context context) {
        super(context);
        init();
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        handleAttributes(attrs, 0, 0);
    }

    public CalendarView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        handleAttributes(attrs, defStyle, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CalendarView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        handleAttributes(attrs, defStyleAttr, defStyleRes);
    }

    public void initDateSelectionListener(DateSelectionListener listener) {
        this.listener = listener;
    }

    private void handleAttributes(AttributeSet attrs, int defStyle, int defStyleRes) {
        settingsManager = new SettingsManager();
        final TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.CalendarView, defStyle, defStyleRes);
        try {
            handleAttributes(typedArray);
            handleWeekendDaysAttributes(typedArray);
        } finally {
            typedArray.recycle();
        }
        init();
    }

    /**
     * Handles custom attributes and sets them to settings manager
     *
     * @param typedArray
     */
    private void handleAttributes(TypedArray typedArray) {
        int orientation = typedArray.getInteger(R.styleable.CalendarView_orientation, SettingsManager.DEFAULT_ORIENTATION);
        int firstDayOfWeek = typedArray.getInteger(R.styleable.CalendarView_firstDayOfTheWeek, SettingsManager.DEFAULT_FIRST_DAY_OF_WEEK);
        int selectionType = typedArray.getInteger(R.styleable.CalendarView_selectionType, SettingsManager.DEFAULT_SELECTION_TYPE);
        boolean showDaysOfWeekTitle = orientation != LinearLayoutManager.HORIZONTAL;
        boolean showDaysOfWeek = orientation == LinearLayoutManager.HORIZONTAL;
        int calendarBackgroundColor = typedArray.getColor(R.styleable.CalendarView_calendarBackgroundColor, ContextCompat.getColor(getContext(), R.color.default_calendar_background_color));
        int monthTextColor = typedArray.getColor(R.styleable.CalendarView_monthTextColor, ContextCompat.getColor(getContext(), R.color.default_month_text_color));
        int otherDayTextColor = typedArray.getColor(R.styleable.CalendarView_otherDayTextColor, ContextCompat.getColor(getContext(), R.color.default_other_day_text_color));
        int dayTextColor = typedArray.getColor(R.styleable.CalendarView_dayTextColor, ContextCompat.getColor(getContext(), R.color.default_day_text_color));
        int weekendDayTextColor = typedArray.getColor(R.styleable.CalendarView_weekendDayTextColor, ContextCompat.getColor(getContext(), R.color.default_weekend_day_text_color));
        int weekDayTitleTextColor = typedArray.getColor(R.styleable.CalendarView_weekDayTitleTextColor, ContextCompat.getColor(getContext(), R.color.default_week_day_title_text_color));
        int selectedDayTextColor = typedArray.getColor(R.styleable.CalendarView_selectedDayTextColor, ContextCompat.getColor(getContext(), R.color.default_selected_day_text_color));
        int selectedDayBackgroundColor = typedArray.getColor(R.styleable.CalendarView_selectedDayBackgroundColor, ContextCompat.getColor(getContext(), R.color.default_selected_day_background_color));
        int selectedDayBackgroundStartColor = typedArray.getColor(R.styleable.CalendarView_selectedDayBackgroundStartColor, ContextCompat.getColor(getContext(), R.color.default_selected_day_background_start_color));
        int selectedDayBackgroundEndColor = typedArray.getColor(R.styleable.CalendarView_selectedDayBackgroundEndColor, ContextCompat.getColor(getContext(), R.color.default_selected_day_background_end_color));
        int currentDayTextColor = typedArray.getColor(R.styleable.CalendarView_currentDayTextColor, ContextCompat.getColor(getContext(), R.color.default_day_text_color));
        int currentDayIconRes = typedArray.getResourceId(R.styleable.CalendarView_currentDayIconRes, 0);
        int currentDaySelectedIconRes = typedArray.getResourceId(R.styleable.CalendarView_currentDaySelectedIconRes, R.drawable.ic_triangle_white);
        int connectedDayIconRes = typedArray.getResourceId(R.styleable.CalendarView_connectedDayIconRes, 0);
        int connectedDaySelectedIconRes = typedArray.getResourceId(R.styleable.CalendarView_connectedDaySelectedIconRes, 0);
        int disabledDayTextColor = typedArray.getColor(R.styleable.CalendarView_disabledDayTextColor, ContextCompat.getColor(getContext(), R.color.default_disabled_day_text_color));
        int selectionBarMonthTextColor = typedArray.getColor(R.styleable.CalendarView_selectionBarMonthTextColor, ContextCompat.getColor(getContext(), R.color.default_selection_bar_month_title_text_color));
        int previousMonthIconRes = typedArray.getResourceId(R.styleable.CalendarView_previousMonthIconRes, R.drawable.ic_chevron_left_gray);
        int nextMonthIconRes = typedArray.getResourceId(R.styleable.CalendarView_nextMonthIconRes, R.drawable.ic_chevron_right_gray);

        setBackgroundColor(calendarBackgroundColor);
        settingsManager.setCalendarBackgroundColor(calendarBackgroundColor);
        settingsManager.setMonthTextColor(monthTextColor);
        settingsManager.setOtherDayTextColor(otherDayTextColor);
        settingsManager.setDayTextColor(dayTextColor);
        settingsManager.setWeekendDayTextColor(weekendDayTextColor);
        settingsManager.setWeekDayTitleTextColor(weekDayTitleTextColor);
        settingsManager.setSelectedDayTextColor(selectedDayTextColor);
        settingsManager.setSelectedDayBackgroundColor(selectedDayBackgroundColor);
        settingsManager.setSelectedDayBackgroundStartColor(selectedDayBackgroundStartColor);
        settingsManager.setSelectedDayBackgroundEndColor(selectedDayBackgroundEndColor);
        settingsManager.setConnectedDayIconRes(connectedDayIconRes);
        settingsManager.setConnectedDaySelectedIconRes(connectedDaySelectedIconRes);
        settingsManager.setDisabledDayTextColor(disabledDayTextColor);
        settingsManager.setCurrentDayTextColor(currentDayTextColor);
        settingsManager.setCurrentDayIconRes(currentDayIconRes);
        settingsManager.setCurrentDaySelectedIconRes(currentDaySelectedIconRes);
        settingsManager.setCalendarOrientation(orientation);
        settingsManager.setFirstDayOfWeek(firstDayOfWeek);
        settingsManager.setShowDaysOfWeek(showDaysOfWeek);
        settingsManager.setShowDaysOfWeekTitle(showDaysOfWeekTitle);
        settingsManager.setSelectionType(selectionType);
    }

    private void handleWeekendDaysAttributes(TypedArray typedArray) {
        if (typedArray.hasValue(R.styleable.CalendarView_weekendDays)) {
            Set<Long> weekendDays = new TreeSet<>();

            int weekdaysAttr = typedArray.getInteger(R.styleable.CalendarView_weekendDays, WeekDay.SUNDAY);
            if (containsFlag(weekdaysAttr, WeekDay.MONDAY))
                weekendDays.add((long) Calendar.MONDAY);
            if (containsFlag(weekdaysAttr, WeekDay.TUESDAY))
                weekendDays.add((long) Calendar.TUESDAY);
            if (containsFlag(weekdaysAttr, WeekDay.WEDNESDAY))
                weekendDays.add((long) Calendar.WEDNESDAY);
            if (containsFlag(weekdaysAttr, WeekDay.THURSDAY))
                weekendDays.add((long) Calendar.THURSDAY);
            if (containsFlag(weekdaysAttr, WeekDay.FRIDAY))
                weekendDays.add((long) Calendar.FRIDAY);
            if (containsFlag(weekdaysAttr, WeekDay.SATURDAY))
                weekendDays.add((long) Calendar.SATURDAY);
            if (containsFlag(weekdaysAttr, WeekDay.SUNDAY))
                weekendDays.add((long) Calendar.SUNDAY);

            settingsManager.setWeekendDays(weekendDays);
        }
    }

    private boolean containsFlag(int attr, int flag) {
        return (attr | flag) == attr;
    }

    private void init() {
        setDaysOfWeekTitles();

        setSelectionManager();
        createRecyclerView();
        createBottomSelectionBar();
    }

    /**
     * Defines days of week displaying according to calendar orientation
     * HORIZONTAL - displaying below month name and above dates
     * VERTICAL - displaying above whole calendar
     */
    private void setDaysOfWeekTitles() {
        settingsManager.setShowDaysOfWeekTitle(settingsManager.getCalendarOrientation() != LinearLayoutManager.HORIZONTAL);
        settingsManager.setShowDaysOfWeek(settingsManager.getCalendarOrientation() == LinearLayoutManager.HORIZONTAL);

        if (llDaysOfWeekTitles == null) {
            createDaysOfWeekTitle();
        }
        if (settingsManager.isShowDaysOfWeekTitle()) {
            showDaysOfWeekTitle();
        } else {
            hideDaysOfWeekTitle();
        }
    }

    /**
     * Creates days of week title above calendar
     */
    private void createDaysOfWeekTitle() {
        boolean isTitleAlreadyAdded = llDaysOfWeekTitles != null;
        if (!isTitleAlreadyAdded) {
            llDaysOfWeekTitles = new LinearLayout(getContext());
            llDaysOfWeekTitles.setId(View.generateViewId());
            llDaysOfWeekTitles.setOrientation(LinearLayout.HORIZONTAL);
            llDaysOfWeekTitles.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            llDaysOfWeekTitles.removeAllViews();
        }

        CalendarUtils.populateDaysOfWeekLayout(settingsManager.getFirstDayOfWeek(), llDaysOfWeekTitles);
    }

    /**
     * Creates bottom selection bar to show selected days
     */
    private void createBottomSelectionBar() {
        flBottomSelectionBar = new FrameLayout(getContext());
        flBottomSelectionBar.setId(View.generateViewId());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, rvMonths.getId());
        flBottomSelectionBar.setLayoutParams(params);
        flBottomSelectionBar.setBackgroundResource(R.drawable.border_top_bottom);
        flBottomSelectionBar.setVisibility(settingsManager.getCalendarOrientation() == OrientationHelper.HORIZONTAL ? View.VISIBLE : View.GONE);
        addView(flBottomSelectionBar);

        createRangeSelectionLayout();
    }

    private void createRangeSelectionLayout() {
        llRangeSelection = (LinearLayout) ((LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.view_selection_bar_range, null);
        llRangeSelection.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        llRangeSelection.setVisibility(GONE);
        flBottomSelectionBar.addView(llRangeSelection);
    }

    private void showDaysOfWeekTitle() {
        llDaysOfWeekTitles.setVisibility(View.VISIBLE);
    }

    private void hideDaysOfWeekTitle() {
        llDaysOfWeekTitles.setVisibility(View.GONE);
    }

    private void setSelectionManager() {
        switch (getSelectionType()) {
            case SelectionType.SINGLE:
                selectionManager = new SingleSelectionManager(this);
                break;

            case SelectionType.RANGE:
                selectionManager = new RangeSelectionManager(this);
                break;

            case SelectionType.NONE:
                selectionManager = new NoneSelectionManager();
                break;
        }
    }

    public BaseSelectionManager getSelectionManager() {
        return selectionManager;
    }

    public void setSelectionManager(BaseSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
        monthAdapter.setSelectionManager(selectionManager);
        update();
    }

    public void update() {
        if (monthAdapter != null) {
            monthAdapter.notifyDataSetChanged();
            rvMonths.scrollToPosition(lastVisibleMonthPosition);
        }
    }

    private void createRecyclerView() {
        rvMonths = new SlowdownRecyclerView(getContext());
        rvMonths.setId(View.generateViewId());
        rvMonths.setHasFixedSize(true);
        rvMonths.setNestedScrollingEnabled(false);
        ((SimpleItemAnimator) rvMonths.getItemAnimator()).setSupportsChangeAnimations(false);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, llDaysOfWeekTitles.getId());
        rvMonths.setLayoutParams(params);

        rvMonths.setLayoutManager(new GridLayoutManager(getContext(), 1, settingsManager.getCalendarOrientation(), false));
        monthAdapter = createAdapter();

        rvMonths.setAdapter(monthAdapter);
        rvMonths.addOnScrollListener(pagingScrollListener);
        rvMonths.getRecycledViewPool().setMaxRecycledViews(ItemViewType.MONTH, 10);
        addView(rvMonths);
    }

    private MonthAdapter createAdapter() {
        return new MonthAdapter.MonthAdapterBuilder()
                .setMonths(CalendarUtils.createInitialMonths(settingsManager))
                .setMonthDelegate(new MonthDelegate(settingsManager))
                .setCalendarView(this)
                .setSelectionManager(selectionManager)
                .setSettingsManager(settingsManager)
                .createMonthAdapter();
    }

    private int getFirstVisiblePosition(RecyclerView.LayoutManager manager) {
        if (manager instanceof LinearLayoutManager) {
            return ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
        } else {
            throw new IllegalArgumentException("Unsupported Layout Manager");
        }
    }

    @Override
    public Calendar getMinDate() {
        return settingsManager.getMinDate();
    }

    @Override
    public void setMinDate(Calendar minDate) {
        settingsManager.setMinDate(minDate);
        monthAdapter.setMinDate(minDate);
    }

    @Override
    public Calendar getMaxDate() {
        return settingsManager.getMaxDate();
    }

    @Override
    public void setMaxDate(Calendar maxDate) {
        settingsManager.setMaxDate(maxDate);
        monthAdapter.setMaxDate(maxDate);
    }

    @Override
    public Set<Long> getDisabledDays() {
        return settingsManager.getDisabledDays();
    }

    public void setDisabledDays(Set<Long> disabledDays) {
        settingsManager.setDisabledDays(disabledDays);
        monthAdapter.setDisabledDays(disabledDays);
    }

    @Override
    public ConnectedDaysManager getConnectedDaysManager() {
        return settingsManager.getConnectedDaysManager();
    }

    @Override
    public Set<Long> getWeekendDays() {
        return settingsManager.getWeekendDays();
    }

    public void setWeekendDays(Set<Long> weekendDays) {
        settingsManager.setWeekendDays(weekendDays);
        monthAdapter.setWeekendDays(weekendDays);
    }

    @Override
    public DisabledDaysCriteria getDisabledDaysCriteria() {
        return settingsManager.getDisabledDaysCriteria();
    }

    @Override
    public void setDisabledDaysCriteria(DisabledDaysCriteria criteria) {
        settingsManager.setDisabledDaysCriteria(criteria);
        monthAdapter.setDisabledDaysCriteria(criteria);
    }

    @Override
    public void addConnectedDays(ConnectedDays connectedDays) {
        settingsManager.getConnectedDaysManager().addConnectedDays(connectedDays);
        recreateInitialMonth();
    }

    /**
     * Removes all selections (manual and by criteria)
     */
    public void clearSelections() {
        selectionManager.clearSelections();
        setSelectionBarVisibility();
        update();
    }

    /**
     * Returns all selected days
     *
     * @return
     */
    public List<Day> getSelectedDays() {
        List<Day> selectedDays = new ArrayList<>();
        for (Iterator<Month> monthIterator = monthAdapter.getData().iterator(); monthIterator.hasNext(); ) {
            Month month = monthIterator.next();
            for (Iterator<Day> dayIterator = month.getDaysWithoutTitlesAndOnlyCurrent().iterator(); dayIterator.hasNext(); ) {
                Day day = dayIterator.next();
                if (selectionManager.isDaySelected(day)) {
                    selectedDays.add(day);
                }
            }
        }
        return selectedDays;
    }

    /**
     * Returns all selected dates
     *
     * @return
     */
    public List<Calendar> getSelectedDates() {
        List<Calendar> selectedDays = new ArrayList<>();
        for (Day day : getSelectedDays()) {
            selectedDays.add(day.getCalendar());
        }
        return selectedDays;
    }

    /**
     * Scroll calendar to previous month
     */
    public void goToPreviousMonth() {
        int currentVisibleItemPosition = ((GridLayoutManager) rvMonths.getLayoutManager()).findFirstVisibleItemPosition();
        if (currentVisibleItemPosition != 0) {
            rvMonths.smoothScrollToPosition(currentVisibleItemPosition - 1);
        }
    }

    /**
     * Scroll calendar to next month
     */
    public void goToNextMonth() {
        int currentVisibleItemPosition = ((GridLayoutManager) rvMonths.getLayoutManager()).findFirstVisibleItemPosition();
        if (currentVisibleItemPosition != monthAdapter.getData().size() - 1) {
            rvMonths.smoothScrollToPosition(currentVisibleItemPosition + 1);
        }
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    private void recreateInitialMonth() {
        monthAdapter.getData().clear();
        monthAdapter.getData().addAll(CalendarUtils.createInitialMonths(settingsManager));
        lastVisibleMonthPosition = SettingsManager.DEFAULT_MONTH_COUNT / 2;
    }

    @Override
    public void onDaySelected() {
        selectedDays = getSelectedDays();
        displaySelectedDays();
        if (listener != null) listener.daySelected();
    }

    /**
     * Displays selected days
     */
    private void displaySelectedDays() {
        switch (settingsManager.getSelectionType()) {
            case SelectionType.RANGE:
                displaySelectedDaysRange();
                break;

            default:
                llRangeSelection.setVisibility(GONE);
                break;
        }
    }

    /**
     * Display selected days for RANGE mode in bottom bar
     */
    private void displaySelectedDaysRange() {
        if (selectionManager instanceof RangeSelectionManager) {
            Pair<Day, Day> days = ((RangeSelectionManager) selectionManager).getDays();
            if (days != null) {
                llRangeSelection.setVisibility(VISIBLE);
                TextView tvStartRangeTitle = llRangeSelection.findViewById(R.id.tv_range_start_date);
                tvStartRangeTitle.setText(CalendarUtils.getYearNameTitle(days.first));

                TextView tvEndRangeTitle = llRangeSelection.findViewById(R.id.tv_range_end_date);
                tvEndRangeTitle.setText(CalendarUtils.getYearNameTitle(days.second));

                CircleAnimationTextView catvStart = llRangeSelection.findViewById(R.id.catv_start);
                catvStart.setText(String.valueOf(days.first.getDayNumber()));
                catvStart.setTextColor(getSelectedDayTextColor());
                catvStart.showAsStartCircle(this, true);

                CircleAnimationTextView catvEnd = llRangeSelection.findViewById(R.id.catv_end);
                catvEnd.setText(String.valueOf(days.second.getDayNumber()));
                catvEnd.setTextColor(getSelectedDayTextColor());
                catvEnd.showAsEndCircle(this, true);

                CircleAnimationTextView catvMiddle = llRangeSelection.findViewById(R.id.catv_middle);
                catvMiddle.showAsRange(this);
            } else {
                llRangeSelection.setVisibility(GONE);
            }
        }
    }

    /**
     * Defines do we need to show range of selected days in bottom selection bar
     *
     * @return
     */
    private boolean needToShowSelectedDaysRange() {
        if (getCalendarOrientation() == OrientationHelper.HORIZONTAL && getSelectionType() == SelectionType.RANGE) {
            if (selectionManager instanceof RangeSelectionManager) {
                return ((RangeSelectionManager) selectionManager).getDays() != null;
            }
        }
        return false;
    }

    /**
     * Sets selection bar layout visibility
     */
    private void setSelectionBarVisibility() {
        flBottomSelectionBar.setVisibility(getCalendarOrientation() == OrientationHelper.HORIZONTAL ? View.VISIBLE : View.GONE);
        llRangeSelection.setVisibility(needToShowSelectedDaysRange() ? View.VISIBLE : View.GONE);
    }

    @Override
    @SelectionType
    public int getSelectionType() {
        return settingsManager.getSelectionType();
    }

    @Override
    public void setSelectionType(@SelectionType int selectionType) {
        settingsManager.setSelectionType(selectionType);
        setSelectionManager();
        monthAdapter.setSelectionManager(selectionManager);
        setSelectionBarVisibility();

        //Clear selections and selection bar
        selectionManager.clearSelections();
        update();
    }

    @Override
    public int getCalendarBackgroundColor() {
        return settingsManager.getCalendarBackgroundColor();
    }

    @Override
    public void setCalendarBackgroundColor(int calendarBackgroundColor) {
        settingsManager.setCalendarBackgroundColor(calendarBackgroundColor);
        setBackgroundColor(calendarBackgroundColor);
    }

    @Override
    public int getMonthTextColor() {
        return settingsManager.getMonthTextColor();
    }

    @Override
    public void setMonthTextColor(int monthTextColor) {
        settingsManager.setMonthTextColor(monthTextColor);
        update();
    }

    @Override
    public int getOtherDayTextColor() {
        return settingsManager.getOtherDayTextColor();
    }

    @Override
    public void setOtherDayTextColor(int otherDayTextColor) {
        settingsManager.setOtherDayTextColor(otherDayTextColor);
        update();
    }

    @Override
    public int getDayTextColor() {
        return settingsManager.getDayTextColor();
    }

    @Override
    public void setDayTextColor(int dayTextColor) {
        settingsManager.setDayTextColor(dayTextColor);
        update();
    }

    @Override
    public int getWeekendDayTextColor() {
        return settingsManager.getWeekendDayTextColor();
    }

    @Override
    public void setWeekendDayTextColor(int weekendDayTextColor) {
        settingsManager.setWeekendDayTextColor(weekendDayTextColor);
        update();
    }

    @Override
    public int getWeekDayTitleTextColor() {
        return settingsManager.getWeekDayTitleTextColor();
    }

    @Override
    public void setWeekDayTitleTextColor(int weekDayTitleTextColor) {
        settingsManager.setWeekDayTitleTextColor(weekDayTitleTextColor);
        for (int i = 0; i < llDaysOfWeekTitles.getChildCount(); i++) {
            ((SquareTextView) llDaysOfWeekTitles.getChildAt(i)).setTextColor(weekDayTitleTextColor);
        }
        update();
    }

    @Override
    public int getSelectedDayTextColor() {
        return settingsManager.getSelectedDayTextColor();
    }

    @Override
    public void setSelectedDayTextColor(int selectedDayTextColor) {
        settingsManager.setSelectedDayTextColor(selectedDayTextColor);
        update();
    }

    @Override
    public int getSelectedDayBackgroundColor() {
        return settingsManager.getSelectedDayBackgroundColor();
    }

    @Override
    public void setSelectedDayBackgroundColor(int selectedDayBackgroundColor) {
        settingsManager.setSelectedDayBackgroundColor(selectedDayBackgroundColor);
        update();
    }

    @Override
    public int getSelectedDayBackgroundStartColor() {
        return settingsManager.getSelectedDayBackgroundStartColor();
    }

    @Override
    public void setSelectedDayBackgroundStartColor(int selectedDayBackgroundStartColor) {
        settingsManager.setSelectedDayBackgroundStartColor(selectedDayBackgroundStartColor);
        update();
    }

    @Override
    public int getSelectedDayBackgroundEndColor() {
        return settingsManager.getSelectedDayBackgroundEndColor();
    }

    @Override
    public void setSelectedDayBackgroundEndColor(int selectedDayBackgroundEndColor) {
        settingsManager.setSelectedDayBackgroundEndColor(selectedDayBackgroundEndColor);
        update();
    }

    @Override
    public int getCurrentDayTextColor() {
        return settingsManager.getCurrentDayTextColor();
    }

    @Override
    public void setCurrentDayTextColor(int currentDayTextColor) {
        settingsManager.setCurrentDayTextColor(currentDayTextColor);
        update();
    }

    @Override
    public int getCurrentDayIconRes() {
        return settingsManager.getCurrentDayIconRes();
    }

    @Override
    public void setCurrentDayIconRes(int currentDayIconRes) {
        settingsManager.setCurrentDayIconRes(currentDayIconRes);
        update();
    }

    @Override
    public int getCurrentDaySelectedIconRes() {
        return settingsManager.getCurrentDaySelectedIconRes();
    }

    @Override
    public void setCurrentDaySelectedIconRes(int currentDaySelectedIconRes) {
        settingsManager.setCurrentDaySelectedIconRes(currentDaySelectedIconRes);
        update();
    }

    @Override
    public int getCalendarOrientation() {
        return settingsManager.getCalendarOrientation();
    }

    @Override
    public void setCalendarOrientation(int calendarOrientation) {
        clearSelections();
        settingsManager.setCalendarOrientation(calendarOrientation);
        setDaysOfWeekTitles();
        recreateInitialMonth();

        rvMonths.setLayoutManager(new GridLayoutManager(getContext(), 1, getCalendarOrientation(), false));
        if (flNavigationButtons != null) {
            flNavigationButtons.setVisibility(GONE);
        }

        setSelectionBarVisibility();
        update();
    }

    @Override
    public int getConnectedDayIconRes() {
        return settingsManager.getConnectedDayIconRes();
    }

    @Override
    public void setConnectedDayIconRes(int connectedDayIconRes) {
        settingsManager.setConnectedDayIconRes(connectedDayIconRes);
        update();
    }

    @Override
    public int getConnectedDaySelectedIconRes() {
        return settingsManager.getConnectedDaySelectedIconRes();
    }

    @Override
    public void setConnectedDaySelectedIconRes(int connectedDaySelectedIconRes) {
        settingsManager.setConnectedDaySelectedIconRes(connectedDaySelectedIconRes);
        update();
    }

    @Override
    public int getConnectedDayIconPosition() {
        return settingsManager.getConnectedDayIconPosition();
    }

    @Override
    public void setConnectedDayIconPosition(int connectedDayIconPosition) {
        settingsManager.setConnectedDayIconPosition(connectedDayIconPosition);
        update();
    }

    @Override
    public int getDisabledDayTextColor() {
        return settingsManager.getDisabledDayTextColor();
    }

    @Override
    public void setDisabledDayTextColor(int disabledDayTextColor) {
        settingsManager.setDisabledDayTextColor(disabledDayTextColor);
        update();
    }

    @Override
    public boolean isShowDaysOfWeek() {
        return settingsManager.isShowDaysOfWeek();
    }

    @Override
    public void setShowDaysOfWeek(boolean showDaysOfWeek) {
        settingsManager.setShowDaysOfWeek(showDaysOfWeek);
        recreateInitialMonth();
    }

    @Override
    public boolean isShowDaysOfWeekTitle() {
        return settingsManager.isShowDaysOfWeekTitle();
    }

    @Override
    public void setShowDaysOfWeekTitle(boolean showDaysOfWeekTitle) {
        settingsManager.setShowDaysOfWeekTitle(showDaysOfWeekTitle);
        if (showDaysOfWeekTitle) {
            showDaysOfWeekTitle();
        } else {
            hideDaysOfWeekTitle();
        }
    }

    @Override
    public int getFirstDayOfWeek() {
        return settingsManager.getFirstDayOfWeek();
    }

    @Override
    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (firstDayOfWeek > 0 && firstDayOfWeek < 8) {
            settingsManager.setFirstDayOfWeek(firstDayOfWeek);
            recreateInitialMonth();
            createDaysOfWeekTitle();
        } else {
            throw new IllegalArgumentException("First day of week must be 1 - 7");
        }
    }

    public interface DateSelectionListener {
        void daySelected();
    }
}