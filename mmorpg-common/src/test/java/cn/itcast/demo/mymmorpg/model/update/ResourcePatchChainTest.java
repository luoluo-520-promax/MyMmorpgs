package cn.itcast.demo.mymmorpg.model.update;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcePatchChainTest {

    @Test
    public void plan_buildsChainAndPreheatUrls() {
        List<ResourcePatchChain.PatchNode> nodes = List.of(
                new ResourcePatchChain.PatchNode(2, "a", 100, "https://cdn/p2", List.of("a.bin", "b.bin"), true),
                new ResourcePatchChain.PatchNode(3, "b", 200, "https://cdn/p3", List.of(), false));
        ResourcePatchChain.DownloadPlan plan = ResourcePatchChain.plan(1, 3, nodes, null);
        assertThat(plan.chain()).hasSize(2);
        assertThat(plan.totalBytes()).isEqualTo(300);
        assertThat(plan.cdnPreheatUrls()).isNotEmpty();
        assertThat(ResourcePatchChain.toView(plan).get("patchCount")).isEqualTo(2);
    }

    @Test
    public void plan_supportsResumeCursor() {
        List<ResourcePatchChain.PatchNode> nodes = List.of(
                new ResourcePatchChain.PatchNode(2, "a", 100, "https://cdn/p2", List.of(), true),
                new ResourcePatchChain.PatchNode(3, "b", 200, "https://cdn/p3", List.of(), true));
        ResourcePatchChain.ResumeCursor resume = new ResourcePatchChain.ResumeCursor(3, "part0", 50);
        ResourcePatchChain.DownloadPlan plan = ResourcePatchChain.plan(1, 3, nodes, resume);
        assertThat(plan.chain()).hasSize(1);
        assertThat(plan.chain().get(0).version()).isEqualTo(3);
        assertThat(plan.totalBytes()).isEqualTo(150);
    }
}
