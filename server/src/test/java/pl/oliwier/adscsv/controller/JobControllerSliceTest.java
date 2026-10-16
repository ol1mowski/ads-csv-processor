package pl.oliwier.adscsv.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.oliwier.adscsv.service.job.JobService;
import pl.oliwier.adscsv.service.upload.JobUploadService;
import pl.oliwier.adscsv.support.SecuredWebSlice;
import pl.oliwier.adscsv.support.TestUsers;

@WebMvcTest(JobController.class)
@Import(SecuredWebSlice.class)
@ActiveProfiles("test")
abstract class JobControllerSliceTest {

  static final UUID JOB_ID = UUID.fromString("0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10");
  static final String JOB_URI = "/api/v1/jobs/" + JOB_ID;
  static final String OWNER = TestUsers.ALICE;

  @Autowired MockMvcTester mvc;

  @MockitoBean JobUploadService uploadService;

  @MockitoBean JobService jobService;

  static RequestPostProcessor owner() {
    return user(OWNER);
  }
}
