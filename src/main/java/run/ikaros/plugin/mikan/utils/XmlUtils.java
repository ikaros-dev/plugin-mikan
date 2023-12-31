package run.ikaros.plugin.mikan.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import run.ikaros.api.infra.utils.SystemVarUtils;
import run.ikaros.plugin.mikan.MikanRssItem;
import run.ikaros.plugin.mikan.exception.RssOperateException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class XmlUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlUtils.class);

    public static List<MikanRssItem> parseMikanRssXmlFile(String filePath) {
        Assert.hasText(filePath, "'filePath' must has text.");
        List<MikanRssItem> mikanRssItemList = new ArrayList<>();

        try {
            File cacheFile = new File(filePath);
            if (!cacheFile.exists()) {
                LOGGER.warn("cache file not exist, will return directly, file path:{}", filePath);
                return mikanRssItemList;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(cacheFile);
            Element element = document.getDocumentElement();
            Node firstChild = element.getFirstChild();
            if (firstChild.getNodeName().contains("#text")) {
                firstChild = firstChild.getNextSibling();
            }
            NodeList childNodes = firstChild.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                Node item1 = childNodes.item(j);
                String nodeName = item1.getNodeName();
                // LOGGER.debug("parse channel item name: {}", nodeName);
                if ("item".equalsIgnoreCase(nodeName)) {
                    MikanRssItem mikanRssItem = handlerItemNode(item1);
                    mikanRssItemList.add(mikanRssItem);
                }
            }
        } catch (IOException | SAXException | ParserConfigurationException exception) {
            throw new RssOperateException("parse xml file fail", exception);
        }
        return mikanRssItemList;
    }

    private static MikanRssItem handlerItemNode(Node item) {
        MikanRssItem mikanRssItem = new MikanRssItem();

        NodeList childNodes = item.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            String nodeName = node.getNodeName();
            String textContent = node.getTextContent();

            if ("link".equalsIgnoreCase(nodeName)) {
                mikanRssItem.setEpisodePageUrl(textContent);
            }
            if ("title".equalsIgnoreCase(nodeName)) {
                mikanRssItem.setTitle(textContent);
            }
            // if ("torrent".equalsIgnoreCase(nodeName)) {
            //  mikanRssItem.setTorrentUrl(node.getFirstChild().getNextSibling().getTextContent());
            // }
            if ("enclosure".equalsIgnoreCase(nodeName)) {
                String url = node.getAttributes().getNamedItem("url").getNodeValue();
                mikanRssItem.setTorrentUrl(url);
            }
        }
        return mikanRssItem;
    }

    public static String generateJellyfinTvShowNfoXml(String filePath, String plot,
                                                      String title,
                                                      String originaltitle, String bangumiid) {
        Assert.hasText(filePath, "'filePath' must has text.");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        try {
            db = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("new document builder fail", e);
        }
        Document document = db.newDocument();
        // 不显示standalone="no"
        document.setXmlStandalone(true);
        Element tvshowElement = document.createElement("tvshow");
        document.appendChild(tvshowElement);

        Element plotElement = document.createElement("plot");
        plotElement.setTextContent(plot);
        tvshowElement.appendChild(plotElement);

        Element lockdataElement = document.createElement("lockdata");
        lockdataElement.setTextContent("false");
        tvshowElement.appendChild(lockdataElement);

        Element titleElement = document.createElement("title");
        titleElement.setTextContent(title);
        tvshowElement.appendChild(titleElement);

        Element originaltitleElement = document.createElement("originaltitle");
        originaltitleElement.setTextContent(originaltitle);
        tvshowElement.appendChild(originaltitleElement);

        Element bangumiidElement = document.createElement("bangumiid");
        bangumiidElement.setTextContent(bangumiid);
        tvshowElement.appendChild(bangumiidElement);

        File file = new File(filePath);
        File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            TransformerFactory tff = TransformerFactory.newInstance();
            Transformer tf = tff.newTransformer();
            // 输出内容是否使用换行
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            // 创建xml文件并写入内容
            tf.transform(new DOMSource(document), new StreamResult(file));
            LOGGER.info("generate jellyfin tv show nfo xml file success, filePath: {}", filePath);
        } catch (TransformerConfigurationException transformerException) {
            LOGGER.warn("generate jellyfin tv show nfo xml file fail", transformerException);
        } catch (javax.xml.transform.TransformerException e) {
            throw new RuntimeException(e);
        }
        return filePath;
    }

    public static String generateJellyfinEpisodeNfoXml(String filePath, String plot,
                                                       String title, String season,
                                                       String episode, String bangumiid) {
        Assert.hasText(filePath, "'filePath' must has text.");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        try {
            db = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("new document builder fail", e);
        }
        Document document = db.newDocument();
        // 不显示standalone="no"
        document.setXmlStandalone(true);
        Element tvshowElement = document.createElement("episodedetails");
        document.appendChild(tvshowElement);

        Element plotElement = document.createElement("plot");
        plotElement.setTextContent(plot);
        tvshowElement.appendChild(plotElement);

        Element lockdataElement = document.createElement("lockdata");
        lockdataElement.setTextContent("false");
        tvshowElement.appendChild(lockdataElement);

        Element titleElement = document.createElement("title");
        titleElement.setTextContent(title);
        tvshowElement.appendChild(titleElement);

        Element seasonElement = document.createElement("season");
        seasonElement.setTextContent(season);
        tvshowElement.appendChild(seasonElement);

        Element episodeElement = document.createElement("episode");
        episodeElement.setTextContent(episode);
        tvshowElement.appendChild(episodeElement);

        Element bangumiidElement = document.createElement("bangumiid");
        bangumiidElement.setTextContent(bangumiid);
        tvshowElement.appendChild(bangumiidElement);

        File file = new File(filePath);
        File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            TransformerFactory tff = TransformerFactory.newInstance();
            Transformer tf = tff.newTransformer();
            // 输出内容是否使用换行
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            // 创建xml文件并写入内容
            tf.transform(new DOMSource(document), new StreamResult(file));
            LOGGER.info("generate jellyfin episode nfo xml file success, filePath: {}", filePath);
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        } catch (javax.xml.transform.TransformerException e) {
            throw new RuntimeException(e);
        }
        return filePath;
    }


    /**
     * 从RSS URL下载内容到本地RSS库
     *
     * @param url   RSS URL
     * @param proxy 是否设置代理
     * @return 缓存文件路径
     */
    public static String downloadRssXmlFile(String url, Proxy proxy) {
        Assert.hasText(url, "'url' must has text.");
        String cacheFilePath = SystemVarUtils.getOsCacheDirPath(null)
            + File.separator
            + UUID.randomUUID().toString().replace("-", "") + ".xml";

        InputStream inputStream = null;
        BufferedInputStream bufferedInputStream = null;
        OutputStream outputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            URLConnection urlConnection;
            if (proxy == null) {
                urlConnection = new URL(url).openConnection();
            } else {
                urlConnection = new URL(url).openConnection(proxy);
            }
            urlConnection.connect();
            inputStream = urlConnection.getInputStream();
            bufferedInputStream = new BufferedInputStream(inputStream);

            File cacheFile = new File(cacheFilePath);
            if (!cacheFile.getParentFile().exists()) {
                cacheFile.getParentFile().mkdirs();
            }
            if (!cacheFile.exists()) {
                cacheFile.createNewFile();
            }
            outputStream = Files.newOutputStream(cacheFile.toPath());
            bufferedOutputStream = new BufferedOutputStream(outputStream);

            byte[] buffer = new byte[128];
            int len;
            while ((len = bufferedInputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, len);
            }
            LOGGER.info("download rss xml file to cache path: {}", cacheFilePath);
        } catch (IOException e) {
            String msg = "fail write rss url xml file bytes to cache path: " + cacheFilePath;
            LOGGER.warn(msg, e);
            throw new RssOperateException(msg, e);
        } finally {
            try {
                bufferedOutputStream.close();
                outputStream.close();
                bufferedInputStream.close();
                inputStream.close();
            } catch (IOException e) {
                throw new RssOperateException(e);
            }
        }
        return cacheFilePath;
    }
}
