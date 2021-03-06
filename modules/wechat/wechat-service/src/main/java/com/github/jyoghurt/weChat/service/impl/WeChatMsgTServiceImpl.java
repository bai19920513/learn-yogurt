package com.github.jyoghurt.weChat.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.jyoghurt.security.securityUnitT.domain.SecurityUnitT;
import com.github.jyoghurt.security.securityUnitT.service.SecurityUnitTService;
import com.github.jyoghurt.weChat.dao.WeChatAutoResponseTMapper;
import com.github.jyoghurt.weChat.dao.WeChatMsgTMapper;
import com.github.jyoghurt.weChat.domain.WeChatMpnewsMsgT;
import com.github.jyoghurt.weChat.domain.WeChatMsgT;
import com.github.jyoghurt.weChat.service.WeChatMpnewsMsgTService;
import com.github.jyoghurt.weChat.service.WeChatMsgTService;
import com.github.jyoghurt.weChat.service.WeChatSendMsgTService;
import com.github.jyoghurt.wechatbasic.common.resp.*;
import com.github.jyoghurt.wechatbasic.common.util.AdvancedUtil;
import com.github.jyoghurt.wechatbasic.common.util.WeiXinConstants;
import com.github.jyoghurt.wechatbasic.common.util.WeixinUtil;
import com.github.jyoghurt.wechatbasic.enums.WeChatAccountType;
import com.github.jyoghurt.wechatbasic.enums.WeChatMsgTypeEnum;
import com.github.jyoghurt.wechatbasic.exception.WeChatException;
import com.github.jyoghurt.core.handle.QueryHandle;
import com.github.jyoghurt.core.result.HttpResultEntity;
import com.github.jyoghurt.core.result.HttpResultHandle;
import com.github.jyoghurt.core.result.QueryResult;
import com.github.jyoghurt.core.service.impl.ServiceSupport;
import com.github.jyoghurt.wechatbasic.common.pojo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.jyoghurt.wechatbasic.common.util.WeixinUtil.getAccessToken;


@Service("weChatMsgTService")
public class WeChatMsgTServiceImpl extends ServiceSupport<WeChatMsgT, WeChatMsgTMapper> implements WeChatMsgTService {
    @Autowired
    private WeChatMsgTMapper weChatMsgTMapper;
    @Autowired
    private WeChatAutoResponseTMapper weChatAutoResponseTMapper;
    @Autowired
    private WeChatMpnewsMsgTService weChatMpnewsMsgTService;
    @Autowired
    private SecurityUnitTService securityUnitTService;
    @Autowired
    private WeChatSendMsgTService weChatSendMsgTService;
    @Value("${uploadPath}")
    private String uploadPath;
    @Value("${downloadPath}")
    private String downloadPath;

    @Override
    public WeChatMsgTMapper getMapper() {
        return weChatMsgTMapper;
    }

    @Override
    public void delete(Serializable id)  {
        getMapper().delete(WeChatMsgT.class, id);
    }

    @Override
    public WeChatMsgT find(Serializable id)  {
        return getMapper().selectById(WeChatMsgT.class, id);
    }

    /*
    * ????????????????????????
    * */
    @Override
    public void add(WeChatMsgT weChatMsgT)  {
        this.save(weChatMsgT);

    }

    @Override
    public void addMpNews(WeChatMsgT weChatMsgT)  {
        WeChatMsgT WeChatMsgT1 = new WeChatMsgT();
        WeChatMsgT1.setAccountType(weChatMsgT.getAccountType());
        WeChatMsgT1.setState(weChatMsgT.getState());
        WeChatMsgT1.setMsgtype("mpnews");
        WeChatMsgT1.setUnitId(weChatMsgT.getUnitId());
        this.save(WeChatMsgT1);
        List<WeChatMpnewsMsgT> weChatMpnewsMsgTlist = weChatMsgT.getList();
        for (int i = 0; i < weChatMpnewsMsgTlist.size(); i++) {
            String imgurl = weChatMpnewsMsgTlist.get(i).getThumUrl().replace(downloadPath, "");
            WeChatMpnewsMsgT weChatMpnewsMsgT = new WeChatMpnewsMsgT();
            weChatMpnewsMsgT.setMessageId(WeChatMsgT1.getMessageId());
            weChatMpnewsMsgT.setNewsTitle(weChatMpnewsMsgTlist.get(i).getNewsTitle());
            weChatMpnewsMsgT.setContent(weChatMpnewsMsgTlist.get(i).getContent());
            weChatMpnewsMsgT.setThumUrl(weChatMpnewsMsgTlist.get(i).getThumUrl());
            weChatMpnewsMsgT.setSort(String.valueOf(i));
            weChatMpnewsMsgT.setShowCoverPic(weChatMpnewsMsgTlist.get(i).getShowCoverPic());
            weChatMpnewsMsgTService.save(weChatMpnewsMsgT);
        }


    }

    @Override
    //TODO
    public List<WeChatMsgT> getNewsList()  {
        List<WeChatMsgT> list1 = getMapper().getNewsList();
        List<WeChatMsgT> list2 = new ArrayList<WeChatMsgT>();
        for (int i = 0; i < list1.size(); i++) {
            WeChatMsgT t = new WeChatMsgT();
            List<WeChatMpnewsMsgT> list = weChatMpnewsMsgTService.getListByMessageId(list1.get(i).getMessageId());
            t.setList(list);
            t.setMessageId(list1.get(i).getMessageId());
            list2.add(t);
        }
        return list2;
    }

    @Override
    //??????????????????
    public void sendWeChat(WeChatMsgT weChatMsgT, String unitId)  {

        Map<String, String> map = new HashMap<String, String>();
        SecurityUnitT unitT = securityUnitTService.findSecretByUnitId(unitId);
        // ???????????????????????????
        String appId = "";
        // ?????????????????????????????????
        String appSecret = "";

        if ((WeChatAccountType.TYPE_SUBSCRIPTION).toString().equals(weChatMsgT.getAccountType())) {  //?????????
            appId = unitT.getAppId();
            appSecret = unitT.getSecretKey();
        } else if ((WeChatAccountType.TYPE_SERVICE).toString().equals(weChatMsgT.getAccountType())) {  //?????????
            appId = unitT.getAppIdF();
            appSecret = unitT.getSecretKeyF();
        } else if ((WeChatAccountType.TYPE_ENTERPRISE).toString().equals(weChatMsgT.getAccountType())) {  //?????????
            appId = unitT.getAppIdQ();
            appSecret = unitT.getSecretKeyQ();
        }
        AccessToken token = getAccessToken(appId, appSecret);

        // ?????????????????????url

        String access_token = token.getToken();


        List<WeChatMpnewsMsgT> list = weChatMsgT.getList();

        Articles[] articles = new Articles[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String filePath = uploadPath + (list.get(i).getThumUrl().replace(this.downloadPath, ""));
            System.out.println(filePath);
            //????????????
            //TODO
            String thumb_media_id = null;
            try {
                thumb_media_id = AdvancedUtil.uploadMaterial(access_token, filePath).getMedia_id();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Articles article = new Articles();
            article.setContent(list.get(i).getContent());
            if (list.get(i).getShowCoverPic()) {
                article.setShow_cover_pic("1");
            } else {
                article.setShow_cover_pic("0");
            }
            article.setTitle(list.get(i).getNewsTitle());
            article.setThumb_media_id(thumb_media_id);
            articles[i] = article;
        }

        NewsMap newsmap = new NewsMap();
        newsmap.setArticles(articles);


        // ????????????????????????????????????????????????id
        String media_id = null;
        try {
            media_id = WeixinUtil.uploadMaterialLibrary(newsmap, access_token);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Filter filter = new Filter();
        filter.setIs_to_all(true);
        Mpnews mpnews = new Mpnews();
        mpnews.setMedia_id(media_id);

        AllToMessage allToMessage = new AllToMessage();
        allToMessage.setFilter(filter);
        allToMessage.setMpnews(mpnews);
        allToMessage.setMsgtype(WeChatMsgTypeEnum.mpnews);
        int su = WeixinUtil.sendAll(allToMessage, access_token);
        if (su == 0) {
            weChatMsgT.setMessageId(weChatMsgT.getMessageId());
            weChatMsgT.setState("1");
            this.update(weChatMsgT);
        }


    }

    //??????????????????
    @Override
    public void sendWeChatText(WeChatMsgT weChatMsgT, String unitId)  {
        SecurityUnitT unitT = securityUnitTService.findSecretByUnitId(unitId);
        // ???????????????????????????
        String appId = "";
        // ?????????????????????????????????
        String appSecret = "";
        if ((WeChatAccountType.TYPE_SUBSCRIPTION).toString().equals(weChatMsgT.getAccountType())) {  //?????????
            appId = unitT.getAppId();
            appSecret = unitT.getSecretKey();
        } else if ((WeChatAccountType.TYPE_SERVICE).toString().equals(weChatMsgT.getAccountType())) {  //?????????
            appId = unitT.getAppIdF();
            appSecret = unitT.getSecretKeyF();
        } else if ((WeChatAccountType.TYPE_ENTERPRISE).toString().equals(weChatMsgT.getAccountType())) {  //?????????
            appId = unitT.getAppIdQ();
            appSecret = unitT.getSecretKeyQ();
        }

        AccessToken token = getAccessToken(appId, appSecret);

        // ?????????????????????url

        String access_token = token.getToken();


        Filter filter = new Filter();
        filter.setIs_to_all(true);
        Text text = new Text();
        text.setContent(weChatMsgT.getTextContent());

        AllToMessage allToMessage = new AllToMessage();
        allToMessage.setFilter(filter);
        allToMessage.setText(text);
        allToMessage.setMsgtype(WeChatMsgTypeEnum.text);


        int su = WeixinUtil.sendAll(allToMessage, access_token);
        if (su == 0) {
            weChatMsgT.setMessageId(weChatMsgT.getMessageId());
            weChatMsgT.setState("1");
            this.update(weChatMsgT);
        }


    }

    public void sendMsgByOpenId(String message)  {
        Filter filter = new Filter();
        filter.setIs_to_all(true);
        //??????message
        JSONObject messageObj = JSON.parseObject(message);
        AccessToken token = getAccessToken(messageObj.get("appId").toString(), messageObj.get("appsecret").toString());
        // ?????????????????????url
        String access_token = token.getToken();
        MessageByOpenId messageByOpenId = new MessageByOpenId();
        Text text = new Text();
        text.setContent(messageObj.getString("context"));
        messageByOpenId.setTouser(messageObj.getString("fromUserId"));
        messageByOpenId.setText(text);
        messageByOpenId.setMsgtype("text");
        WeixinUtil.sendByOpenId(messageByOpenId, access_token);
    }

    /*????????????????????????????????????*/
    @Override
    public String uploadMaterialNews(MaterialNewsMap materialNewsMap, WeChatAccountType weChatAccountType,
                                     SecurityUnitT unitT, String ifsend) throws WeChatException {
        NewsList newsList = new NewsList();
        List<Articles> articles = materialNewsMap.getContent().getNews_item();
        for (Articles article : articles) {
            /*????????????img?????????url????????????????????????????????????url????????????????????????src??????*/
            List<String> imgSrcList = getImgSrc(article.getContent());
            String content = article.getContent();
            for (String src : imgSrcList) {
                if(src.endsWith("gif")){
                    continue;
                }
                content = content.replace(src, AdvancedUtil.uploadImgByInterLink(getTokenByUnit(unitT, weChatAccountType), src));
            }
            article.setContent(content);
        }
        newsList.setArticles(articles);
        String mediaId = AdvancedUtil.uploadMaterialNews(getTokenByUnit(unitT, weChatAccountType), newsList).getMedia_id();
        materialNewsMap.setMedia_id(mediaId);
        if (WeiXinConstants.SEND.equals(ifsend)) {
            weChatSendMsgTService.sendAll(mediaId, weChatAccountType, WeChatMsgTypeEnum.mpnews,
                    unitT);
        }
        uploadThumb(mediaId, weChatAccountType, unitT);
        return mediaId;
    }

    /*???????????????????????????*/
    @Override
    public MaterialNewsMap updateMaterialNews(MaterialNewsMap materialNewsMap, WeChatAccountType weChatAccountType,
                                              SecurityUnitT unitT, String ifsend) throws  WeChatException {
        for (int i = 0; i < materialNewsMap.getContent().getNews_item().size(); i++) {
            UpdateMaterialNewsMap updateMaterialNewsMap = new UpdateMaterialNewsMap();
            /*??????????????????*/
            updateMaterialNewsMap.setMedia_id(materialNewsMap.getMedia_id());
            updateMaterialNewsMap.setIndex(i);
            Articles article = materialNewsMap.getContent().getNews_item().get(i);
            /*???????????????????????????????????????*/
            List<String> imgSrcList = getImgSrc(article.getContent());
            String content = article.getContent();
            for (String src : imgSrcList) {
                if(src.endsWith("gif")){
                    continue;
                }
                content = content.replace(src, AdvancedUtil.uploadImgByInterLink(getTokenByUnit(unitT, weChatAccountType), src));
            }
            article.setContent(content);
            updateMaterialNewsMap.setArticles(article);
            AdvancedUtil.updateMaterialNews(getTokenByUnit(unitT, weChatAccountType), updateMaterialNewsMap);
            if (WeiXinConstants.SEND.equals(ifsend)) {
                /*????????????????????????*/
                weChatSendMsgTService.sendAll(materialNewsMap.getMedia_id(), weChatAccountType, WeChatMsgTypeEnum.mpnews,
                        unitT);
            }
        }
        uploadThumb(materialNewsMap.getMedia_id(), weChatAccountType, unitT);
        return materialNewsMap;
    }

    /**
     * ????????????????????????????????????id?????????????????????????????????????????????????????????media_id?????????????????????
     * ???????????????id???????????????????????????????????????????????????????????????
     *
     * @param mediaId
     * @param weChatAccountType
     * @param unitT
     * @throws WeChatException
     */
    public void uploadThumb(String mediaId, WeChatAccountType weChatAccountType,
                            SecurityUnitT unitT) throws WeChatException {
        String appId = "";
        String appSecret = "";
        switch (weChatAccountType) {
            case TYPE_SUBSCRIPTION: {
                appId = unitT.getAppId();
                appSecret = unitT.getSecretKey();
                break;
            }
            case TYPE_SERVICE: {
                appId = unitT.getAppIdF();
                appSecret = unitT.getSecretKeyF();
                break;
            }

            case TYPE_ENTERPRISE: {
                appId = unitT.getAppIdQ();
                appSecret = unitT.getSecretKeyQ();
                break;
            }
        }
        /*??????token*/
        AccessToken token = getAccessToken(appId, appSecret);
              /*??????mediaId????????????????????????????????????????????????????????????id???????????????*/
        MaterialNewsContent materialNewsContent = AdvancedUtil.getMaterialNews(token.getToken(), mediaId);
        String downLoadFilePath = StringUtils.join(uploadPath, "/", appId, "/");
        for (Articles article : materialNewsContent.getNews_item()) {
            AdvancedUtil.getMaterial(getTokenByUnit(unitT, weChatAccountType), article.getThumb_media_id(),
                    downLoadFilePath, ".jpg");
        }
    }

    @Override
    public void delMaterial(String media_id, WeChatAccountType weChatAccountType, SecurityUnitT unitT) throws
            WeChatException {
        AdvancedUtil.delMaterial(getTokenByUnit(unitT, weChatAccountType), media_id);
    }

    @Override
    public void preView(String towxname, String media_id, WeChatAccountType weChatAccountType, SecurityUnitT unitT) throws  WeChatException {
        PreViewParam preViewParam = new PreViewParam();
        preViewParam.setTowxname(towxname);
        Mpnews mpnews = new Mpnews();
        mpnews.setMedia_id(media_id);
        preViewParam.setMpnews(mpnews);
        AdvancedUtil.preView(getTokenByUnit(unitT, weChatAccountType), preViewParam);
    }

    @Override
    public QueryResult<MaterialNewsMap> batchgetMaterialNews(QueryHandle queryHandle, WeChatAccountType
            weChatAccountType, SecurityUnitT unitT) throws  WeChatException {
        String appId = "";
        String appSecret = "";
        switch (weChatAccountType) {
            case TYPE_SUBSCRIPTION: {
                appId = unitT.getAppId();
                appSecret = unitT.getSecretKey();
                logger.info("???????????????appId???:"+appId+"appsecret??????"+appSecret);
                break;
            }
            case TYPE_SERVICE: {
                appId = unitT.getAppIdF();
                appSecret = unitT.getSecretKeyF();
                logger.info("???????????????appId???:"+appId+"appsecret??????"+appSecret);
                break;
            }

            case TYPE_ENTERPRISE: {
                appId = unitT.getAppIdQ();
                appSecret = unitT.getSecretKeyQ();
                logger.info("???????????????appId???:"+appId+"appsecret??????"+appSecret);
                break;
            }
        }
        /*??????token*/
        AccessToken token = getAccessToken(appId, appSecret);
        BathgetMaterialParam bathgetMaterialParam = new BathgetMaterialParam();
        int startNum = (queryHandle.getPage() - 1) * queryHandle.getRows();
        bathgetMaterialParam.setOffset(startNum);
        bathgetMaterialParam.setCount(startNum + queryHandle.getRows());
        QueryResult<MaterialNewsMap> queryResult = this.newQueryResult();
        //????????????????????????;
        MaterialNewsMapList materialNewsMapList = AdvancedUtil.batchgetMaterialNews(token.getToken(),
                bathgetMaterialParam);
        for (MaterialNewsMap materialNewsMap : materialNewsMapList.getItem()) {
            String downLoadFilePath = StringUtils.join(downloadPath, "/", appId);
            for (Articles articles : materialNewsMap.getContent().getNews_item()) {
                articles.setDownloadPath(downLoadFilePath + "/" + articles.getThumb_media_id() + ".jpg");
            }
        }
        queryResult.setData(materialNewsMapList.getItem());
        queryResult.setRecordsTotal(Long.valueOf(materialNewsMapList.getTotal_count()));
        return queryResult;
    }

    @Override
    public HttpResultEntity<?> getMaterial(String media_id, WeChatAccountType weChatAccountType, WeChatMsgTypeEnum
            weChatMsgTypeEnum, SecurityUnitT unitT) throws  WeChatException {
        String appId = "";
        String appSecret = "";
        switch (weChatAccountType) {
            case TYPE_SUBSCRIPTION: {
                appId = unitT.getAppId();
                appSecret = unitT.getSecretKey();
                break;
            }
            case TYPE_SERVICE: {
                appId = unitT.getAppIdF();
                appSecret = unitT.getSecretKeyF();
                break;
            }

            case TYPE_ENTERPRISE: {
                appId = unitT.getAppIdQ();
                appSecret = unitT.getSecretKeyQ();
                break;
            }
        }
        /*??????token*/
        AccessToken token = getAccessToken(appId, appSecret);
        MaterialNewsMap materialNewsMap = new MaterialNewsMap();
        switch (weChatMsgTypeEnum) {
            case mpnews: {
                materialNewsMap.setMedia_id(media_id);
                materialNewsMap.setContent(AdvancedUtil.getMaterialNews(token.getToken(), media_id));
                String downLoadFilePath = StringUtils.join(downloadPath, "/", appId);
                for (Articles articles : materialNewsMap.getContent().getNews_item()) {
                    articles.setDownloadPath(downLoadFilePath + "/" + articles.getThumb_media_id() + ".jpg");
                }
                break;
            }
        }
        return HttpResultHandle.getSuccessResult(materialNewsMap);
    }

    public String getTokenByUnit(SecurityUnitT unitT, WeChatAccountType
            weChatAccountType) {
        String appId = "";
        String appSecret = "";
        switch (weChatAccountType) {
            case TYPE_SUBSCRIPTION: {
                appId = unitT.getAppId();
                appSecret = unitT.getSecretKey();
                break;
            }
            case TYPE_SERVICE: {
                appId = unitT.getAppIdF();
                appSecret = unitT.getSecretKeyF();
                break;
            }

            case TYPE_ENTERPRISE: {
                appId = unitT.getAppIdQ();
                appSecret = unitT.getSecretKeyQ();
                break;
            }
        }
        /*??????token*/
        AccessToken token = getAccessToken(appId, appSecret);
        return token.getToken();
    }

    /*????????????html??????img src??????*/
    public static final Pattern PATTERN = Pattern.compile("<img\\s+(?:[^>]*)src\\s*=\\s*([^>]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static List getImgSrc(String html) {
        Matcher matcher = PATTERN.matcher(html);
        List list = new ArrayList();
        while (matcher.find()) {
            String group = matcher.group(1);
            if (group == null) {
                continue;
            }
            //   ???????????????????????????????????????,????????????src="...."?????????????????????
            if (group.startsWith("'")) {
                list.add(group.substring(1, group.indexOf("'", 1)));
            } else if (group.startsWith("\"")) {
                list.add(group.substring(1, group.indexOf("\"", 1)));
            } else {
                list.add(group.split("\\s")[0]);
            }
        }
        return list;
    }
}
