package kr.kimchimap.global.status.controller;

import kr.kimchimap.global.status.dto.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
  @GetMapping("/status")
  public SystemStatus status() {
    return new SystemStatus("국산김치맵", "UP");
  }
}
