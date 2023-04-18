package com.hmdp.utils;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendMailTool {

    private static MimeMessage message = null;
    private static Session session = null;
    //工具类,不需要实例化类,私有化使该类不能实例化。

    private SendMailTool(){
    }

    private static void initMailSender(String sender, String receiver) throws MessagingException {
        if (message != null){message = null;}
        if(session != null){session = null;}
        String senderRegex = "[1-9]\\d{7,10}@qq\\.com";
        String receiverRegex = "^\\s*\\w+(?:\\.{0,1}[\\w-]+)*@[a-zA-Z0-9]+(?:[-.][a-zA-Z0-9]+)*\\.[a-zA-Z]+\\s*$";
        if (RegexNotMatch(senderRegex, sender)){
            throw new AddressException("Illegal sender", sender);
        }
        if (RegexNotMatch(receiverRegex, receiver)){
            throw new AddressException("Illegal receiver", receiver);
        }
        //跟真正的邮箱建立链接   提交很多信息
        Properties properties = new Properties();
        properties.put("mail.transport.protocol","smtp");//设置协议
        properties.put("mail.smtp.host","smtp.qq.com");//设置主机号
        session = Session.getInstance(properties);//创建一个会话
        //创建一个虚拟的邮件对象
        message = new MimeMessage(session);
        //设置内部属性        发件人,收件人,主题,时间,附件。。。
        message.setFrom(new InternetAddress(sender));//发件人
        message.setRecipient(MimeMessage.RecipientType.TO,new InternetAddress(receiver));//发送方式和收件人,To 直接发送   CC 抄送   BCC 密送
        message.setSubject("尊敬的用户:"+receiver.substring(0,receiver.lastIndexOf("@"))+",您好");//主题
        message.setSentDate(new Date());//设置发送时间
    }

    private static boolean sendIt(String sender, String smtpPassword) throws MessagingException {
        //创建一个发送者
        Transport transport = session.getTransport();
        //得到一个邮箱认证
        transport.connect(sender,smtpPassword);
        //发送邮件
        transport.sendMessage(message,message.getAllRecipients());
        transport.close();
        return true;
    }

    public static boolean TextMailBySMTPQQ(String sender,String smtpPassword,String receiver,String text) throws MessagingException {
        initMailSender(sender,receiver);
        message.setText(text);
        message.saveChanges();
        return sendIt(sender, smtpPassword);
    }
    private static boolean RegexNotMatch(String Regex, String CheckString){
        Pattern regex = Pattern.compile(Regex);
        Matcher matcher = regex.matcher(CheckString);
        return !matcher.matches();
    }
}
