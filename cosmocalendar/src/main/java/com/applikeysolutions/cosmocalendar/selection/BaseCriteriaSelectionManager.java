package com.applikeysolutions.cosmocalendar.selection;

import com.applikeysolutions.cosmocalendar.model.Day;
import com.applikeysolutions.cosmocalendar.selection.criteria.BaseCriteria;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseCriteriaSelectionManager extends BaseSelectionManager {

    protected List<BaseCriteria> criteriaList;

    public BaseCriteriaSelectionManager() {
    }

    public void setCriteriaList(List<BaseCriteria> criteriaList) {
        this.criteriaList = new ArrayList<>(criteriaList);
        notifyCriteriaUpdates();
    }

    public void clearCriteriaList() {
        if (criteriaList != null) {
            criteriaList.clear();
        }
        notifyCriteriaUpdates();
    }

    private void notifyCriteriaUpdates() {
        if (onDaySelectedListener != null) {
            onDaySelectedListener.onDaySelected();
        }
    }

    public boolean hasCriteria() {
        return criteriaList != null && !criteriaList.isEmpty();
    }

    public boolean isDaySelectedByCriteria(Day day) {
        if (hasCriteria()) {
            for (BaseCriteria criteria : criteriaList) {
                if (criteria.isCriteriaPassed(day)) {
                    return true;
                }
            }
        }
        return false;
    }
}
