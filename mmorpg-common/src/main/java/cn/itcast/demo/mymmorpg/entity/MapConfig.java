/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-common/src/main/java/cn/itcast/demo/mymmorpg/entity/MapConfig.java
 * 2) 所属模块：mmorpg-common / entity
 * 3) 主要职责：地图配置实体，映射 map_config 表。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column; // JPA 列映射
import jakarta.persistence.Entity; // JPA 实体标记
import jakarta.persistence.GeneratedValue; // 主键生成策略
import jakarta.persistence.GenerationType; // 主键生成类型枚举
import jakarta.persistence.Id; // 主键字段
import jakarta.persistence.Table; // 表名映射

/** 地图静态配置，对应表 map_config */
@Entity // 标记为 JPA 实体
@Table(name = "map_config") // 对应数据库表 map_config
public class MapConfig {

    @Id // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键
    private Integer id; // 地图配置 ID

    @Column(name = "name", nullable = false, length = 64) // 地图名称列
    private String name; // 地图显示名

    @Column(name = "width", nullable = false) // 地图宽度列
    private Integer width; // 地图宽度（格子数）

    @Column(name = "height", nullable = false) // 地图高度列
    private Integer height; // 地图高度（格子数）

    @Column(name = "default_lines", nullable = false) // 默认分线数列
    private Integer defaultLines = 1; // 默认线路数量

    /** AOI 可视半径（世界坐标单位），默认 300 */
    @Column(name = "aoi_radius", nullable = false)
    private Integer aoiRadius = 300;

    /** 九宫格格子边长，默认 100 */
    @Column(name = "grid_size", nullable = false)
    private Integer gridSize = 100;

    /** 推荐进入等级 */
    @Column(name = "recommend_level")
    private Integer recommendLevel;

    public Integer getId() { // 获取地图 ID
        return id;
    }

    public void setId(Integer id) { // 设置地图 ID
        this.id = id; // 赋值 ID
    }

    public String getName() { // 获取地图名称
        return name; // 返回名称
    }

    public void setName(String name) { // 设置地图名称
        this.name = name; // 赋值名称
    }

    public Integer getWidth() { // 获取宽度
        return width; // 返回宽度
    }

    public void setWidth(Integer width) { // 设置宽度
        this.width = width; // 赋值宽度
    }

    public Integer getHeight() { // 获取高度
        return height; // 返回高度
    }

    public void setHeight(Integer height) { // 设置高度
        this.height = height; // 赋值高度
    }

    public Integer getDefaultLines() { // 获取默认分线数
        return defaultLines; // 返回分线数
    }

    public void setDefaultLines(Integer defaultLines) { // 设置默认分线数
        this.defaultLines = defaultLines; // 赋值分线数
    }

    public Integer getAoiRadius() {
        return aoiRadius;
    }

    public void setAoiRadius(Integer aoiRadius) {
        this.aoiRadius = aoiRadius;
    }

    public Integer getGridSize() {
        return gridSize;
    }

    public void setGridSize(Integer gridSize) {
        this.gridSize = gridSize;
    }

    public Integer getRecommendLevel() {
        return recommendLevel;
    }

    public void setRecommendLevel(Integer recommendLevel) {
        this.recommendLevel = recommendLevel;
    }
}
