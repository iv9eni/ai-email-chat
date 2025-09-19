package ai.email.processor;

import ai.email.processor.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class BasicEmailManager implements EmailManager {

    @Autowired
    private JavaMailSender javaMailSender;

    public void respond(Response response) {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setTo(response.to());
        simpleMailMessage.setSubject(response.subject());
        simpleMailMessage.setText(response.body());

        javaMailSender.send(simpleMailMessage);
    }
}
