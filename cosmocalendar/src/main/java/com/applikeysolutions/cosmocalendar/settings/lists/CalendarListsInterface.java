package com.applikeysolutions.cosmocalendar.settings.lists;

import com.applikeysolutions.cosmocalendar.settings.lists.connected_days.ConnectedDays;
import com.applikeysolutions.cosmocalendar.settings.lists.connected_days.ConnectedDaysManager;

import java.util.Calendar;
import java.util.Set;

public interface CalendarListsInterface {

    Calendar getMinDate();

    void setMinDate(Calendar minDate);

    Calendar getMaxDate();

    void setMaxDate(Calendar maxDate);

    Set<Long> getDisabledDays();

    void setDisabledDays(Set<Long> disabledDays);

    ConnectedDaysManager getConnectedDaysManager();

    Set<Long> getWeekendDays();

    void setWeekendDays(Set<Long> weekendDays);

    DisabledDaysCriteria getDisabledDaysCriteria();

    void setDisabledDaysCriteria(DisabledDaysCriteria criteria);

    void addConnectedDays(ConnectedDays connectedDays);
}
