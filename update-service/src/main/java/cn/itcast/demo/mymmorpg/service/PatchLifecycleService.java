package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.ClientVersionRelease;
import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 补丁生命周期：全量新版本发布后归档更早的差分版本，控制补丁链膨胀。
 */
@Service
public class PatchLifecycleService {

    private final ClientVersionReleaseRepository releaseRepository;
    private int keepActiveVersions = 2;

    public PatchLifecycleService(ClientVersionReleaseRepository releaseRepository) {
        this.releaseRepository = releaseRepository;
    }

    @Value("${game.update.keep-active-versions:2}")
    void setKeepActiveVersions(int keepActiveVersions) {
        this.keepActiveVersions = Math.max(1, keepActiveVersions);
    }

    /**
     * 保留最近 N 个 active 版本，更早的标记 archived（active=false）。
     */
    @Transactional
    public int archiveOlderThanKeepWindow() {
        List<ClientVersionRelease> active = releaseRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()))
                .sorted(Comparator.comparing(ClientVersionRelease::getVersionNumber).reversed())
                .toList();
        if (active.size() <= keepActiveVersions) {
            return 0;
        }
        int archived = 0;
        for (int i = keepActiveVersions; i < active.size(); i++) {
            ClientVersionRelease r = active.get(i);
            r.setActive(false);
            releaseRepository.save(r);
            archived++;
        }
        return archived;
    }

    @Transactional
    public boolean archiveVersion(String versionCode) {
        return releaseRepository.findByVersionCode(versionCode).map(r -> {
            r.setActive(false);
            releaseRepository.save(r);
            return true;
        }).orElse(false);
    }
}
