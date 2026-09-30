/**
 * 文件说明
 * 模块：mmorpg-common / 实体
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/entity/MonsterConfig.java
 * 类型：类
 * 职责：定义 MonsterConfig，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.entity;


import jakarta.persistence.Column;

import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;

import jakarta.persistence.GenerationType;

import jakarta.persistence.Id;

import jakarta.persistence.Table;

/**
 * 对应表 monster_config。
 */
@Entity
@Table(name = "monster_config")
public class MonsterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** 标识（类型：Integer） */
    private Integer id;@Column(name = "name", nullable = false, length = 64)
    /** 名称（类型：String） */
    private String name;@Column(name = "model_id", nullable = false)
    /** model标识（类型：Integer） */
    private Integer modelId;@Column(name = "level", nullable = false)
    /** 等级（类型：Integer） */
    private Integer level = 1;@Column(name = "hp_max", nullable = false)
    /** hpmax（类型：Integer） */
    private Integer hpMax = 100;@Column(name = "mp_max", nullable = false)
    /** mpmax（类型：Integer） */
    private Integer mpMax = 0;@Column(name = "attack", nullable = false)
    /** attack（类型：Integer） */
    private Integer attack = 10;@Column(name = "defense", nullable = false)
    /** defense（类型：Integer） */
    private Integer defense = 5;@Column(name = "exp_reward", nullable = false)
    /** 经验reward（类型：Integer） */
    private Integer expReward = 0;    @Column(name = "description", length = 255)
    /** description（类型：String） */
    private String description;

    /** 所属地图 ID；null/0 表示全局模板（兼容旧数据） */
    @Column(name = "map_id")
    private Integer mapId;

    @Column(name = "spawn_x")
    private Float spawnX;

    @Column(name = "spawn_z")
    private Float spawnZ;

    /** 死亡后刷新秒数，默认 30；0 表示不刷新 */
    @Column(name = "respawn_seconds")
    private Integer respawnSeconds = 30;

    /** 1=普通 2=精英 3=Boss */
    @Column(name = "elite_flag")
    private Integer eliteFlag = 1;

    /**
     * 获取标识属性值
     */
    public Integer getId() {
        return id;
    }

    /**
     * 设置标识属性值
     */
    public void setId(Integer id) {
        this.id = id;  // 访问或赋值当前实例字段
    }

    /**
     * 获取名称属性值
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称属性值
     */
    public void setName(String name) {
        this.name = name;  // 访问或赋值当前实例字段
    }

    /**
     * 获取model标识属性值
     */
    public Integer getModelId() {
        return modelId;
    }

    /**
     * 设置model标识属性值
     */
    public void setModelId(Integer modelId) {
        this.modelId = modelId;  // 访问或赋值当前实例字段
    }

    /**
     * 获取等级属性值
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * 设置等级属性值
     */
    public void setLevel(Integer level) {
        this.level = level;  // 访问或赋值当前实例字段
    }

    /**
     * 获取hpmax属性值
     */
    public Integer getHpMax() {
        return hpMax;
    }

    /**
     * 设置hpmax属性值
     */
    public void setHpMax(Integer hpMax) {
        this.hpMax = hpMax;  // 访问或赋值当前实例字段
    }

    /**
     * 获取mpmax属性值
     */
    public Integer getMpMax() {
        return mpMax;
    }

    /**
     * 设置mpmax属性值
     */
    public void setMpMax(Integer mpMax) {
        this.mpMax = mpMax;  // 访问或赋值当前实例字段
    }

    /**
     * 获取attack属性值
     */
    public Integer getAttack() {
        return attack;
    }

    /**
     * 设置attack属性值
     */
    public void setAttack(Integer attack) {
        this.attack = attack;  // 访问或赋值当前实例字段
    }

    /**
     * 获取defense属性值
     */
    public Integer getDefense() {
        return defense;
    }

    /**
     * 设置defense属性值
     */
    public void setDefense(Integer defense) {
        this.defense = defense;  // 访问或赋值当前实例字段
    }

    /**
     * 获取经验reward属性值
     */
    public Integer getExpReward() {
        return expReward;
    }

    /**
     * 设置经验reward属性值
     */
    public void setExpReward(Integer expReward) {
        this.expReward = expReward;  // 访问或赋值当前实例字段
    }

    /**
     * 获取description属性值
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置description属性值
     */
    public void setDescription(String description) {
        this.description = description;  // 访问或赋值当前实例字段
    }

    public Integer getMapId() {
        return mapId;
    }

    public void setMapId(Integer mapId) {
        this.mapId = mapId;
    }

    public Float getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(Float spawnX) {
        this.spawnX = spawnX;
    }

    public Float getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(Float spawnZ) {
        this.spawnZ = spawnZ;
    }

    public Integer getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(Integer respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    public Integer getEliteFlag() {
        return eliteFlag;
    }

    public void setEliteFlag(Integer eliteFlag) {
        this.eliteFlag = eliteFlag;
    }
}
