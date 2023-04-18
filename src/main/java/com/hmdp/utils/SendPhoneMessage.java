// This file is auto-generated, don't edit it. Thanks.
package com.hmdp.utils;

public class SendPhoneMessage {

    private static final String accessKeyId = "LTAI5tFNqwyrQHNF1MhcRTNz";
    private static final String accessKeySecret = "dfQ6eNgOMrMTCZz6NuJV2XwecU9Cbk";
    /**
     * 使用AK&SK初始化账号Client
     *
     * @return Client a
     * @throws Exception 所有异常
     */
    private static com.aliyun.dysmsapi20170525.Client createClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                // 必填，您的 AccessKey ID
                .setAccessKeyId(SendPhoneMessage.accessKeyId)
                // 必填，您的 AccessKey Secret
                .setAccessKeySecret(SendPhoneMessage.accessKeySecret);
        return new com.aliyun.dysmsapi20170525.Client(config);
    }

    public static boolean SendMessage(String phoneNumber,String identifyCode) {
        com.aliyun.dysmsapi20170525.models.SendSmsRequest sendSmsRequest = new com.aliyun.dysmsapi20170525.models.SendSmsRequest()
                .setSignName("阿里云短信测试")
                .setTemplateCode("SMS_154950909")
                .setPhoneNumbers(phoneNumber)
                .setTemplateParam("{\"code\":\""+identifyCode+"\"}");
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            com.aliyun.dysmsapi20170525.Client client = SendPhoneMessage.createClient();
            client.sendSmsWithOptions(sendSmsRequest, runtime);
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

}