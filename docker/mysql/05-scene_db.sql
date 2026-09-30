-- scene 库：钩锁锚点 / 载具（大世界立体移动基建）
CREATE DATABASE IF NOT EXISTS scene_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE scene_db;

CREATE TABLE IF NOT EXISTS map_grapple_nodes (
    node_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
    world_id      INT          NOT NULL,
    x             FLOAT        NOT NULL,
    y             FLOAT        NOT NULL,
    z             FLOAT        NOT NULL,
    capture_radius FLOAT       NOT NULL DEFAULT 3,
    cooldown_ms   BIGINT       NOT NULL DEFAULT 1500,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_grapple_world (world_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS player_vehicle (
    vehicle_id    VARCHAR(64)  NOT NULL PRIMARY KEY,
    owner_id      BIGINT       NOT NULL,
    kind          VARCHAR(16)  NOT NULL DEFAULT 'LAND',
    durability    INT          NOT NULL DEFAULT 100,
    fuel          FLOAT        NOT NULL DEFAULT 100,
    fuel_cap      FLOAT        NOT NULL DEFAULT 100,
    max_speed     FLOAT        NOT NULL DEFAULT 18,
    accel         FLOAT        NOT NULL DEFAULT 6,
    scene_x       FLOAT        NULL,
    scene_y       FLOAT        NULL,
    scene_z       FLOAT        NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_vehicle_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS creature_template (
    species           VARCHAR(64) NOT NULL PRIMARY KEY,
    mount_speed       FLOAT       NOT NULL DEFAULT 12,
    mount_jump        FLOAT       NOT NULL DEFAULT 8,
    mount_stamina_mul FLOAT       NOT NULL DEFAULT 1.2,
    model_id          INT         NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS land_plots (
    plot_id   VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_id  BIGINT      NOT NULL,
    state     VARCHAR(16) NOT NULL DEFAULT 'EMPTY',
    updated_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_land_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crop_instances (
    crop_id       VARCHAR(64) NOT NULL PRIMARY KEY,
    plot_id       VARCHAR(64) NOT NULL,
    seed_id       VARCHAR(64) NOT NULL,
    plant_time_ms BIGINT      NOT NULL,
    grow_ms       BIGINT      NOT NULL,
    water_count   INT         NOT NULL DEFAULT 0,
    quality       VARCHAR(16) NOT NULL DEFAULT 'COMMON',
    KEY idx_crop_plot (plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
