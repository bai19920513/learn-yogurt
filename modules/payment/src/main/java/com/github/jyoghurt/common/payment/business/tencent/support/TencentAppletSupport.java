package com.github.jyoghurt.common.payment.business.tencent.support;

import com.github.jyoghurt.common.payment.business.tencent.common.config.TencentConfigure;
import com.github.jyoghurt.common.payment.business.tencent.common.constants.TencentPaymentConstants;
import com.github.jyoghurt.common.payment.business.tencent.common.support.TencentCommonSupport;
import com.github.jyoghurt.common.payment.business.tencent.common.utils.TencentPayCommonUtil;
import com.github.jyoghurt.common.payment.business.tencent.common.utils.TencentSignatureUtil;
import com.github.jyoghurt.common.payment.business.tencent.enums.TencentTypeEnum;
import com.github.jyoghurt.common.payment.common.domain.PaymentRecordsT;
import com.github.jyoghurt.common.payment.common.enums.PaymentCloseEnum;
import com.github.jyoghurt.common.payment.common.enums.PaymentGatewayEnum;
import com.github.jyoghurt.common.payment.common.enums.PaymentRefundEnum;
import com.github.jyoghurt.common.payment.common.enums.PaymentStateEnum;
import com.github.jyoghurt.common.payment.common.exception.PaymentPreviousErrorException;
import com.github.jyoghurt.common.payment.common.module.RefundRequest;
import com.github.jyoghurt.common.payment.common.service.PaymentRecordsService;
import com.github.jyoghurt.common.payment.common.support.BasePaymentSupport;
import com.github.jyoghurt.common.payment.common.utils.CommonUtil;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * user:DELL
 * date:2017/6/30.
 */
@Component
public class TencentAppletSupport extends BasePaymentSupport {
    @Autowired
    private PaymentRecordsService paymentRecordsService;
    @Autowired
    private TencentCommonSupport tencentCommonSupport;
    @Override
    public Object createPreviousOrder(PaymentRecordsT paymentRecordsT) throws PaymentPreviousErrorException {
        Map<String, Object> vars = paymentRecordsT.getDataAreaMap();
        paymentRecordsT.setPaymentMethod(PaymentGatewayEnum.TENCENT_APPLET);
        TencentConfigure tencentConfigure = TencentPayCommonUtil.initTencentPayConfigs(paymentRecordsT);
        Map<String, Object> responseMap = tencentCommonSupport.tencentPayment(TencentTypeEnum.APPLET, paymentRecordsT);
        JSONObject appletApi = new JSONObject();
        appletApi.put("appId", vars.get("appletAppId"));
        appletApi.put("timeStamp", String.valueOf(new Date().getTime()));
        appletApi.put("nonceStr", CommonUtil.getRandomStr());
        appletApi.put("package", "prepay_id=" + responseMap.get(TencentPaymentConstants.PREPAY_ID).toString());
        appletApi.put("signType", "MD5");
        appletApi.put("paySign", TencentSignatureUtil.getSign(appletApi,tencentConfigure));
        //???????????????????????????Id?????????
        paymentRecordsService.previousPayment(responseMap.get(TencentPaymentConstants.PREPAY_ID).toString(),
                PaymentGatewayEnum.TENCENT_APPLET, paymentRecordsT.getPaymentId());
        return appletApi;
    }

    /**
     * ??????????????????
     *
     * @param paymentRecordsT ??????????????????
     * @return PaymentResult<String>  ????????????
     */
    @Override
    public PaymentStateEnum queryPaymentResult(PaymentRecordsT paymentRecordsT) {
        return tencentCommonSupport.queryPaymentResult(paymentRecordsT.setPaymentMethod(PaymentGatewayEnum.TENCENT_APPLET));
    }

    /**
     * @param paymentRecordsT ??????????????????
     * @return PaymentResult<PaymentCloseEnum>
     */
    @Override
    public PaymentCloseEnum closePayment(PaymentRecordsT paymentRecordsT) {
        return tencentCommonSupport.closePayment(paymentRecordsT.setPaymentMethod(PaymentGatewayEnum.TENCENT_APPLET));
    }

    /**
     * @param refundRequest   ????????????
     * @param paymentRecordsT ????????????* @return ??????????????????
     * @return ??????????????????
     */
    @Override
    public PaymentRefundEnum refundPayment(RefundRequest refundRequest, PaymentRecordsT paymentRecordsT) {
        return tencentCommonSupport.refundPayment(refundRequest, paymentRecordsT);
    }
}
