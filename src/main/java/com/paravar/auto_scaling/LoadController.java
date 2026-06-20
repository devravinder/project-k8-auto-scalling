package com.paravar.auto_scaling;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class LoadController {

    private static final List<byte[]> memoryList = Collections.synchronizedList(new ArrayList<>());

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

    @GetMapping("/api/memory-load")
    public String generateMemoryLoad() {
        // Allocate 50MB of memory
        byte[] chunk = new byte[50 * 1024 * 1024];
        Arrays.fill(chunk, (byte) 1); // Fill with non-zero bytes to ensure physical allocation
        memoryList.add(chunk);

        // Schedule cleanup after 2 minutes so we don't leak memory indefinitely
        new Thread(() -> {
            try {
                Thread.sleep(120_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!memoryList.isEmpty()) {
                memoryList.remove(chunk);
                System.gc(); // Suggest Garbage Collector to run
                System.out.println("Memory chunk cleared");
            }
        }).start();

        long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        return "Allocated 50MB. Active chunks: " + memoryList.size() + 
               " | JVM Heap (Free/Total): " + freeMem + "MB / " + totalMem + "MB";
    }

    @GetMapping("/api/memory-clear")
    public String clearMemory() {
        memoryList.clear();
        System.gc();
        return "All allocated memory chunks cleared";
    }
}
