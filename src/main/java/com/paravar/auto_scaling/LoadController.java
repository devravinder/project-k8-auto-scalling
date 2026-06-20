package com.paravar.auto_scaling;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class LoadController {

    @GetMapping("/api/health")
   public String health(){
        return "OK";
    }

    @GetMapping("/api/load")
    public String generateLoad(){

        // CPU intensive work
        double result = 0;
        for(int i = 0; i < 1_000_000; i++){
            result += Math.sqrt(i) + Math.tan(i);
        }
        System.out.println("Result: "+result);
        return  "Load processed: "+result;
    }


}
