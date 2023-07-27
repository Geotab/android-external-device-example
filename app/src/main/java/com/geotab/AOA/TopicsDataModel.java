package com.geotab.AOA;

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
}
