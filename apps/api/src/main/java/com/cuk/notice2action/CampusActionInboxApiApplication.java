package com.cuk.notice2action;

import com.cuk.notice2action.extraction.service.notice.NoticeFeedProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(NoticeFeedProperties.class)
public class CampusActionInboxApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(CampusActionInboxApiApplication.class, args);
  }
}
