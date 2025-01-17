package dev.matheuscruz;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.util.Map;

@Path("/sns")
public class SNSResource {

    @POST
    public void sns(Map<String, String> body) {
        System.out.println("Receiving SNS message: " + body);
    }
}
