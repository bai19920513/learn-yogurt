package com.github.jyoghurt.common.payment.common.service;

import com.github.jyoghurt.common.payment.business.card.service.CardPayService;
import com.github.jyoghurt.common.payment.business.cash.service.CashPayService;
import com.github.jyoghurt.common.payment.common.domain.PaymentRecordsT;
import com.github.jyoghurt.common.payment.common.enums.PaymentCloseEnum;
import com.github.jyoghurt.common.payment.common.enums.PaymentGatewayEnum;
import com.github.jyoghurt.common.payment.common.enums.PaymentStateEnum;
import com.github.jyoghurt.common.payment.common.exception.*;
import com.github.jyoghurt.common.payment.common.factory.PaymentExtensionFactory;
import com.github.jyoghurt.common.payment.common.factory.PaymentListenerFactory;
import com.github.jyoghurt.common.payment.common.listener.PaymentListener;
import com.github.jyoghurt.common.payment.common.module.PaymentRecordResult;
import com.github.jyoghurt.common.payment.common.module.PaymentRequest;
import com.github.jyoghurt.common.payment.common.module.RefundRequest;
import com.github.jyoghurt.core.exception.BaseErrorException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;

/**
 * user:dell
 * date: 2016/5/17.
 */
@Service
public class PaymentManagerService {
    private static Logger logger = LoggerFactory.getLogger(PaymentManagerService.class);

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentValidateService paymentValidateService;
    @Autowired
    private CardPayService cardPayService;
    @Autowired
    private CashPayService cashPayService;
    @Autowired
    private PaymentRecordsService paymentRecordsService;

    /**
     * ??????????????????
     *
     * @param paymentRequest ????????????
     * @return ????????????????????????
     */
    public PaymentRecordResult prePaymentRecords(PaymentRequest paymentRequest) {
        return paymentRecordsService.prePaymentRecords(paymentRequest);
    }

    /**
     * ???????????????????????????
     *
     * @param paymentId          ????????????Id
     * @param paymentGatewayEnum ????????????
     * @return ??????????????????
     * @throws PaymentRefundedException
     * @throws PaymentPreRepeatException
     * @throws PaymentRepeatException
     * @throws PaymentClosedException
     */
    public PaymentRecordResult createPaymentRecordAndPay(String paymentId, PaymentGatewayEnum paymentGatewayEnum) throws PaymentRefundedException, PaymentPreRepeatException, PaymentRepeatException, PaymentClosedException, PaymentPreviousErrorException {
        PaymentRecordResult paymentRecordResult = paymentRecordsService.reBuildPaymentRecords(paymentId);
        switch (paymentGatewayEnum) {
            case CASH_PAY:
            case CARD_PAY:
                return paymentRecordResult;
            case TENCENT_JSAPI:
            case TENCENT_PAY:
            case TENCENT_APPLET:
            case ALI_PAY:
                paymentRecordResult.setPrePaymentMsg(createPreviousOrder(paymentGatewayEnum, paymentRecordResult.getPaymentId()));
                return paymentRecordResult;
            default:
                throw new BaseErrorException("");
        }
    }

    /**
     * ???????????????
     *
     * @param paymentGatewayEnum ????????????
     * @param paymentId          ????????????Id
     * @return ???????????????
     */
    public Object createPreviousOrder(PaymentGatewayEnum paymentGatewayEnum, String paymentId) throws PaymentRefundedException, PaymentPreRepeatException, PaymentRepeatException, PaymentClosedException, PaymentPreviousErrorException {
        PaymentRecordsT paymentRecordsT = paymentRecordsService.find(paymentId);
        return createPreviousOrder(paymentGatewayEnum, paymentRecordsT);
    }

    /**
     * ???????????????
     *
     * @param paymentGatewayEnum ????????????
     * @param paymentRecordsT    ????????????
     * @return ???????????????
     */
    public Object createPreviousOrder(PaymentGatewayEnum paymentGatewayEnum, PaymentRecordsT paymentRecordsT) throws PaymentRefundedException, PaymentPreRepeatException, PaymentRepeatException, PaymentClosedException, PaymentPreviousErrorException {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes())
                .getRequest();
        java.util.Enumeration e = request.getHeaderNames();
        while (e.hasMoreElements()) {
            String headerName = (String) e.nextElement();
            logger.info("????????????????????????requestName:{},??????requestValue:{}",
                    headerName, request.getHeader(headerName));
        }

        //??????????????????????????????????????? ????????????session???openId??????????????????
        if (PaymentGatewayEnum.TENCENT_JSAPI == paymentGatewayEnum) {
            //???Session?????????OpenId
            String openId = WebUtils.getCookie(request, "openId") == null
                    ? null
                    : WebUtils.getCookie(request, "openId").getValue();
            logger.info("????????????????????????openId:{}", openId);//???????????????openId
            if (StringUtils.isEmpty(openId)) {
                throw new BaseErrorException("???????????????????????????,SESSION?????????openId???paymentId:{0}", paymentRecordsT.getPaymentId
                        ());
            }
            paymentRecordsT.setDataAreaMap("tencentOpenId", openId);
        }
        //?????????????????????
        PaymentListener paymentListener = PaymentListenerFactory.produce(paymentRecordsT
                .getPaymentBusinessType()
                .getServiceName());
        //????????????????????????????????????
        List<String> businessIds = paymentRecordsService.findBusinessIdsByPaymentId(paymentRecordsT.getPaymentId());
        //????????????????????????
        assert paymentListener != null;
        paymentListener.beforePreviousPayment(paymentGatewayEnum, businessIds);
        PaymentStateEnum paymentState = paymentValidateService.verifyAdvancePayment(paymentRecordsT);
        //?????????????????????????????????????????????
        switch (paymentState) {
            case SUCCESS:
                throw new PaymentRepeatException();
            case CLOSED:
                throw new PaymentClosedException();
            case NOTPAY:
                return paymentService.createPreviousOrder(paymentGatewayEnum, paymentRecordsT);
            default:
                throw new BaseErrorException("validateRealPaymentAndSync,?????????????????????????????????????????????,????????????:{0},paymentId:{1}",
                        paymentState.name(), paymentRecordsT.getPaymentId());
        }
    }

    /**
     * ????????????
     *
     * @param paymentRecordsT ??????????????????
     * @return PaymentResult<Boolean>  true ???????????? false????????????
     */
    private PaymentCloseEnum closePayment(PaymentRecordsT paymentRecordsT) {
        return paymentService.closePayment(paymentRecordsT);
    }


    /**
     * ??????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????????????????
     *
     * @param paymentId ????????????Id
     */
    public void closePaymentsByPaymentId(String paymentId) {
        List<PaymentRecordsT> paymentRecords = paymentRecordsService.findPaymentRecords(paymentId);
        for (PaymentRecordsT paymentRecordsT : paymentRecords) {
            //????????????????????????????????????
            if (paymentRecordsT.getPaymentId().equals(paymentId)) {
                continue;
            }
            //???????????????????????????????????????????????????????????????
            //????????????????????????????????????????????????????????????????????????????????????
            if (paymentRecordsT.getPaymentMethod() != PaymentGatewayEnum.TENCENT_PAY &&
                    paymentRecordsT.getPaymentMethod() != PaymentGatewayEnum.TENCENT_JSAPI &&
                    paymentRecordsT.getPaymentMethod() != PaymentGatewayEnum.TENCENT_APPLET &&
                    paymentRecordsT.getPaymentMethod() != PaymentGatewayEnum.ALI_PAY) {
                continue;
            }
            //??????????????????????????????????????????????????????????????????
            if (!paymentRecordsT.getPaymentState()) {
                //?????????????????????????????????????????????????????????
                closePayment(paymentRecordsT);
            }
        }
    }

    /**
     * ????????????
     * ???????????????????????? ????????????
     *
     * @param paymentId     ????????????Id
     * @param paymentAmount ??????????????????
     * @return ????????????
     * @throws CashPayVerifyException
     * @throws PaymentClosedException
     * @throws PaymentRepeatException
     * @throws PaymentRefundedException
     */
    public PaymentStateEnum createCashPayment(String paymentId, BigDecimal paymentAmount) throws CashPayVerifyException, PaymentRepeatException, PaymentRefundedException, PaymentClosedException {
        PaymentRecordsT paymentRecordsT = paymentRecordsService.find(paymentId);
        return createCashPayment(paymentRecordsT, paymentAmount);
    }

    /**
     * ????????????
     * ???????????????????????? ????????????
     *
     * @param paymentRecordsT ????????????
     * @param paymentAmount   ??????????????????
     * @return ????????????
     * @throws CashPayVerifyException
     * @throws PaymentClosedException
     * @throws PaymentRepeatException
     * @throws PaymentRefundedException
     */
    public PaymentStateEnum createCashPayment(PaymentRecordsT paymentRecordsT, BigDecimal paymentAmount) throws CashPayVerifyException, PaymentRepeatException, PaymentRefundedException, PaymentClosedException {
        if (paymentRecordsT.getDeleteFlag()) {
            throw new PaymentClosedException();
        }
        //????????????????????????????????????
        List<String> businessIds = paymentRecordsService.findBusinessIdsByPaymentId(paymentRecordsT.getPaymentId());
        //?????????????????????
        PaymentListener paymentListener = PaymentListenerFactory.produce(paymentRecordsT
                .getPaymentBusinessType()
                .getServiceName());
        //????????????????????????
        assert paymentListener != null;
        paymentListener.beforePayment(PaymentGatewayEnum.CARD_PAY, businessIds);
        //???????????????????????????????????????????????????
        closePaymentsByPaymentId(paymentRecordsT.getPaymentId());
        return cashPayService.cashPay(paymentRecordsT, paymentAmount);
    }

    /**
     * ????????????
     * ????????????????????????
     *
     * @param paymentId ????????????Id
     * @return ????????????
     * @throws PaymentClosedException
     * @throws PaymentRepeatException
     * @throws PaymentRefundedException
     */
    public PaymentStateEnum cardPayment(String paymentId) throws PaymentClosedException, PaymentRepeatException, PaymentRefundedException {
        PaymentRecordsT paymentRecordsT = paymentRecordsService.find(paymentId);
        return cardPayment(paymentRecordsT);
    }

    /**
     * ????????????
     * ????????????????????????
     *
     * @param paymentRecordsT ????????????
     * @return ????????????
     * @throws PaymentClosedException
     * @throws PaymentRepeatException
     * @throws PaymentRefundedException
     */
    public PaymentStateEnum cardPayment(PaymentRecordsT paymentRecordsT) throws PaymentClosedException, PaymentRepeatException, PaymentRefundedException {
        if (paymentRecordsT.getDeleteFlag()) {
            throw new PaymentClosedException();
        }
        //????????????????????????????????????
        List<String> businessId = paymentRecordsService.findBusinessIdsByPaymentId(paymentRecordsT.getPaymentId());
        //?????????????????????
        PaymentListener paymentListener = PaymentListenerFactory.produce(paymentRecordsT
                .getPaymentBusinessType()
                .getServiceName());
        //????????????????????????
        assert paymentListener != null;
        paymentListener.beforePayment(PaymentGatewayEnum.CARD_PAY, businessId);
        //???????????????????????????????????????????????????
        closePaymentsByPaymentId(paymentRecordsT.getPaymentId());
        return cardPayService.cardPay(paymentRecordsT);
    }

    /**
     * ????????????
     *
     * @param refundRequest ??????????????????
     */
    public void submitRefundPayment(RefundRequest refundRequest) throws PaymentRefundErrorException {
        paymentService.refundPayment(refundRequest);
    }

    /**
     * ?????????????????? ????????????????????????
     *
     * @param paymentId ????????????Id
     * @return ??????????????????
     */
    public Object extensionMethods(String paymentId) {
        PaymentRecordsT paymentRecordsT = paymentRecordsService.findSuccessPaymentRecordsByPaymentId(paymentId);
        if (null == paymentRecordsT) {
            logger.info("?????????????????????", paymentId);
            return null;
        }
        //????????????????????????????????????
        List<String> businessIds = paymentRecordsService.findBusinessIdsByPaymentId(paymentRecordsT.getPaymentId());
        BasePaymentService basePaymentService = PaymentExtensionFactory.produce(paymentRecordsT
                .getPaymentBusinessType()
                .getServiceName());
        //?????????????????????????????????
        return basePaymentService.extensionMethods(businessIds, paymentRecordsT);
    }
}
