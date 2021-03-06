package com.virtualsoundnw.sftpbot.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.virtualsoundnw.sftpbot.domain.SftpTestCase;
import com.virtualsoundnw.sftpbot.domain.Sftproot;
import com.virtualsoundnw.sftpbot.repository.SftpTestCaseRepository;
import com.virtualsoundnw.sftpbot.repository.SftprootRepository;
import com.virtualsoundnw.sftpbot.web.rest.errors.BadRequestAlertException;
import com.virtualsoundnw.sftpbot.web.rest.util.CleanupFiles;
import com.virtualsoundnw.sftpbot.web.rest.util.MonitorFiles;
import com.virtualsoundnw.sftpbot.web.rest.util.StopMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URISyntaxException;
import java.util.List;

/**
 * REST controller for managing tests.
 * <p>
 * This class reads the SftpRoot and TestCase entities then executes the tests they define.
 * <p>
 * This service runs the tests:
 * 1 Reads the sftpRoot and testCase data sent via rest from the DB
 * 2 Creates the sftp root directory structure for each sftpRoot that has test cases
 * 3 Goes into a loop waiting for changes (file creation) to the input directory. For each:
 *  a. Get the file name of the new file
 *  b. loop through test cases checking for matches
 *  c. on a match create the output file in the result directory if a result dir was set in the testCase, or
 *     else in the error dir
 *  d. the testCase inckdues the content, so write that into t a file with the correct name in the correct dir
 *  e. break;
 */
@RestController
@RequestMapping("/api")
public class TestResource {

    private final Logger log = LoggerFactory.getLogger(TestResource.class);

    private final SftprootRepository sftprootRepository;
    private final SftpTestCaseRepository sftpTestCaseRepository;
    private static MonitorFiles monitorFiles;

    public TestResource(SftprootRepository sftprootRepository, SftpTestCaseRepository testCaseRepository) {
        this.sftprootRepository = sftprootRepository;
        this.sftpTestCaseRepository = testCaseRepository;
    }

    /**
     * POST  /begin  : Starts a previously defined test.
     * <p>
     * The test ID passed in must match the ID of an sftpRoot object.
     * The matching sftpRoot will be set up and it's test cases run.
     * A subsequent delete of the same ID will end the test monitoring.
     * @param id the sftpRoot object ID to begin  testing.
     * @return Activate test: all test case rules for this sftpRoot are enforced.
     * @throws URISyntaxException if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the test is already running
     * @throws BadRequestAlertException 400 (Bad Request) if the test does not exist
     */
    @PostMapping("/begin/{id}")
    @Timed
    public ResponseEntity<Sftproot> beginRootTest(@PathVariable Long id) throws URISyntaxException {
        log.debug("REST request to begin testing");

        Sftproot sftpRoot = sftprootRepository.findOne(id);
        log.debug("Make test directories for the sftpRoot. "
            + "Incoming: " + sftpRoot.getIncomingDirectory()
            + ", outgoing: "+ sftpRoot.getOutgoingDirectory()
            + ", error: "+ sftpRoot.getErrorDirectory()
        );
/*
        List<Sftproot> sftpRoots = sftprootRepository.findAll();
        for (Sftproot sftpRoot: sftpRoots) {
            log.debug("Make test directories for the next next sftpRoot: "
                + sftpRoot.getIncomingDirectory() + ", "
                + sftpRoot.getOutgoingDirectory() + ", "
                + sftpRoot.getErrorDirectory();
            );
            new File(sftpRoot.getIncomingDirectory()).mkdir();
            new File(sftpRoot.getOutgoingDirectory()).mkdir();
            new File(sftpRoot.getErrorDirectory()).mkdir();
        }
 */
        List<SftpTestCase> sftpTestCases = sftpTestCaseRepository.findAll();
        if (monitorFiles == null) {
            monitorFiles = new MonitorFiles(sftpRoot, sftpTestCases);
            new Thread(monitorFiles).start();
        }
        return new ResponseEntity<>(sftpRoot, HttpStatus.OK);
    }


    /**
     * POST  /cleanup  : Cleans up the file system for an sftproot.
     * <p>
     * The test ID passed in must match the ID of an sftpRoot object.
     * The matching sftpRoot will be have it's directories flushed - all files deleted.
     * @param id the sftpRoot object ID to clwan up.
     * @return All three directories had a rm * attempt.
     * @throws URISyntaxException if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the test does not exist
     */
    @PostMapping("/cleanup/{id}")
    @Timed
    public ResponseEntity<Sftproot> cleanupRootTest(@PathVariable Long id) throws URISyntaxException {
        log.debug("REST request to begin cleanup");

        Sftproot sftpRoot = sftprootRepository.findOne(id);
        log.debug("Make test directories for the sftpRoot. "
            + "Incoming: " + sftpRoot.getIncomingDirectory()
            + ", outgoing: "+ sftpRoot.getOutgoingDirectory()
            + ", error: "+ sftpRoot.getErrorDirectory()
        );
        CleanupFiles.initCleanupFiles(sftpRoot);
        CleanupFiles.cleanupFiles();
        return new ResponseEntity<>(sftpRoot, HttpStatus.OK);
    }

    /**
     * DELETE /begin    : Stop the MonitorFiles thread via interrupt, if it is available
     *
     * @return status 200 (OK)
     */
    @DeleteMapping("/begin")
    @Timed
    public ResponseEntity<Void> deleteRootTest() {
        log.debug("REST request to delete testing.");
        // need to cancel the test
        new StopMonitor();
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
