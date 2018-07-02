package com.applikeysolutions.cosmocalendar.adapter.viewholder;

import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.applikeysolutions.cosmocalendar.adapter.DaysAdapter;
import com.applikeysolutions.cosmocalendar.model.Month;
import com.applikeysolutions.cosmocalendar.settings.SettingsManager;
import com.applikeysolutions.cosmocalendar.view.MonthView;
import com.applikeysolutions.customizablecalendar.R;

public class MonthHolder extends RecyclerView.ViewHolder {

    private LinearLayout llMonthHeader;
    private TextView tvMonthName;
    private MonthView monthView;
    private SettingsManager appearanceModel;
    private LinearLayout llWeekDays;

    public MonthHolder(View itemView, SettingsManager appearanceModel) {
        super(itemView);
        llMonthHeader = itemView.findViewById(R.id.ll_month_header);
        monthView = itemView.findViewById(R.id.month_view);
        tvMonthName = itemView.findViewById(R.id.tv_month_name);
        llWeekDays = itemView.findViewById(R.id.ll_week_days_title);
        this.appearanceModel = appearanceModel;
    }

    public void setDayAdapter(DaysAdapter adapter) {
        getMonthView().setAdapter(adapter);
    }

    public void bind(Month month, int firstDayOfWeek) {
        tvMonthName.setText(month.getMonthName());
        tvMonthName.setTextColor(appearanceModel.getMonthTextColor());
        llMonthHeader.setBackgroundResource(appearanceModel.getCalendarOrientation() == OrientationHelper.HORIZONTAL ? R.drawable.border_top_bottom : 0);
        CalendarUtils.populateDaysOfWeekLayout(firstDayOfWeek, llWeekDays);
        monthView.initAdapter(month);
    }

    public MonthView getMonthView() {
        return monthView;
    }
}
