package run.ikaros.plugin.mikan.qbittorrent;

public class StrTest {
    public static void main(String[] args) {
        String str = "https://bgm.tv/subject/400215";
        System.out.println(str.substring(str.lastIndexOf("/") + 1));
    }
}
