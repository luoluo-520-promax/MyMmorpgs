package cn.itcast.demo.mymmorpg.gacha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GachaBannerConfig {
    private int id;
    private int bannerType;
    private String name = "";
    private long beginTime;
    private long endTime;
    private List<Integer> rateUpItems5 = new ArrayList<>();
    private List<Integer> rateUpItems4 = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBannerType() {
        return bannerType;
    }

    public void setBannerType(int bannerType) {
        this.bannerType = bannerType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public List<Integer> getRateUpItems5() {
        return rateUpItems5;
    }

    public void setRateUpItems5(List<Integer> rateUpItems5) {
        this.rateUpItems5 = rateUpItems5 == null ? new ArrayList<>() : rateUpItems5;
    }

    public List<Integer> getRateUpItems4() {
        return rateUpItems4;
    }

    public void setRateUpItems4(List<Integer> rateUpItems4) {
        this.rateUpItems4 = rateUpItems4 == null ? new ArrayList<>() : rateUpItems4;
    }

    public boolean isActive(long now) {
        return (beginTime <= 0 || now >= beginTime) && (endTime <= 0 || now <= endTime);
    }
}
