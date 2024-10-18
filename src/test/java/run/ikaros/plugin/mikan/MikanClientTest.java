package run.ikaros.plugin.mikan;

import org.junit.jupiter.api.Test;
import run.ikaros.api.infra.utils.AssertUtils;
import run.ikaros.api.infra.utils.StringUtils;

import static org.junit.jupiter.api.Assertions.*;

class MikanClientTest {

    // @Test
    void getAnimePageUrlByEpisodePageUrl() {
        MikanClient client = new MikanClient(null);
        String epPageUrl = "https://mikanime.tv/Home/Episode/17ec9127dcd4accbbc4e9d8bf624515f56e32d8a";
        String animePageUrl = client.getAnimePageUrlByEpisodePageUrl(epPageUrl);
        assertTrue(StringUtils.isNotBlank(animePageUrl), "'animePageUrl' should not be empty");
    }

    // @Test
    void getBgmTvSubjectPageUrlByAnimePageUrl() {
        String animePageUrl = "https://mikanime.tv/Home/Bangumi/3423#370";
        MikanClient client = new MikanClient(null);
        String bgmTvSubjectPageUrl = client.getBgmTvSubjectPageUrlByAnimePageUrl(animePageUrl);
        assertTrue(StringUtils.isNotBlank(bgmTvSubjectPageUrl), "'bgmTvSubject' should not be empty");

        int index = bgmTvSubjectPageUrl.lastIndexOf("/");
        String bgmTvSubjectId = bgmTvSubjectPageUrl.substring(index + 1);
        assertTrue(StringUtils.isNotBlank(bgmTvSubjectId), "'bgmTvSubjectId' should not be empty");

    }
}