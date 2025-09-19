package ai.email.processor;

import ai.email.processor.model.Prompt;
import ai.email.processor.model.Response;

public interface EmailManager {
    void respond(Response response);
}
