package com.geotab.AOA;

import java.util.Objects;

public class TopicsDataModel {
    public String name;
    public int id = 0;
    public boolean subscribed =false;

    public TopicsDataModel(String name, int id, boolean subscribed) {
        this.name=name;
        this.id=id;
        this.subscribed=subscribed;
    }

    @Override
    public String toString() {
        return "TopicsDataModel{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", subscribed=" + subscribed +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopicsDataModel that = (TopicsDataModel) o;
        return id == that.id && subscribed == that.subscribed && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, subscribed);
    }
}
