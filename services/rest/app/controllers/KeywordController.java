/**
 * 
 */
package controllers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.ats.common.MapBuilder;
import org.ats.common.PageList;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsMavenJob;
import org.ats.service.report.Report;
import org.ats.service.report.ReportService;
import org.ats.service.report.ReportService.Type;
import org.ats.service.report.function.SuiteReport;
import org.ats.services.OrganizationContext;
import org.ats.services.executor.ExecutorService;
import org.ats.services.executor.job.AbstractJob;
import org.ats.services.executor.job.KeywordJob;
import org.ats.services.keyword.CaseFactory;
import org.ats.services.keyword.CaseReference;
import org.ats.services.keyword.CaseService;
import org.ats.services.keyword.CustomKeywordService;
import org.ats.services.keyword.KeywordProject;
import org.ats.services.keyword.KeywordProjectFactory;
import org.ats.services.keyword.KeywordProjectService;
import org.ats.services.keyword.Suite;
import org.ats.services.keyword.SuiteReference;
import org.ats.services.keyword.SuiteService;
import org.ats.services.organization.acl.Authenticated;
import org.ats.services.organization.entity.Tenant;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.vmachine.VMachine;
import org.ats.services.vmachine.VMachineService;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import actions.CorsComposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 28, 2015
 */
@CorsComposition.Cors
@Authenticated
public class KeywordController extends Controller {

  @Inject CaseService caseService;
  
  @Inject CaseFactory caseFactory;
  
  @Inject ReferenceFactory<CaseReference> caseRefFactory;
  
  @Inject SuiteService suiteService;
  
  @Inject KeywordProjectFactory keywordProjectFactory;
  
  @Inject OrganizationContext context;
  
  @Inject ReferenceFactory<SuiteReference> suiteRefFactory;
  
  @Inject KeywordProjectService keywordProjectService;
  
  @Inject ExecutorService executorService;
  
  @Inject ReportService reportService;
  
  @Inject CustomKeywordService customKeywordService;
  
  @Inject VMachineService vmachineService;
  
  private SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yyyy HH:mm");
  
  public Result list() {
    Tenant currentTenant = context.getTenant();
    PageList<KeywordProject> list = keywordProjectService.query(new BasicDBObject("tenant", new BasicDBObject("_id", currentTenant.getId())));
    ArrayNode array = Json.newObject().arrayNode();
    
    while(list.hasNext()) {
      for (KeywordProject project : list.next()) {
        project.put("type", "keyword");
        project.put("totalSuites", suiteService.getSuites(project.getId()).count());
        project.put("totalCases", caseService.getCases(project.getId()).count());
        
        BasicDBObject query = new BasicDBObject("project_id", project.getId()).append("status", AbstractJob.Status.Completed.toString());
        PageList<AbstractJob<?>> jobList = executorService.query(query, 1);
        jobList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
        
        if (jobList.totalPage() > 0) {
          AbstractJob<?> lastJob = jobList.next().get(0);
          project.put("lastRunning", formater.format(lastJob.getCreatedDate()));
          project.put("lastJobId", lastJob.getId());
          
          List<SuiteReference> suites = ((KeywordJob) lastJob).getSuites();
          if (suites.size() > 0) {
            BasicDBList lastSuites = new BasicDBList();
            for (SuiteReference suite : suites) lastSuites.add(suite.toJSon());
            project.put("lastSuites", lastSuites);
          }
        }
        array.add(Json.parse(project.toString()));
      }
    }
    return ok(array);
  }
  
  public Result viewLog(String projectId) {
    BasicDBObject query = new BasicDBObject("project_id", projectId);
    PageList<AbstractJob<?>> jobList = executorService.query(query, 1);
    jobList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    String log = "";
    if (jobList.totalPage() > 0) {
      AbstractJob<?> lastJob = jobList.next().get(0);
      log = lastJob.getLog();
    }
    
    return status(200, log);
  }
  
  public Result get(String projectId) {
    KeywordProject project = keywordProjectService.get(projectId,"show_action","value_delay");
    if (project == null) return status(404);
    
    project.put("type", "keyword");
    project.put("totalSuites", suiteService.getSuites(project.getId()).count());
    project.put("totalCases", caseService.getCases(project.getId()).count());
    
    PageList<AbstractJob<?>> jobList = executorService.query(new BasicDBObject("project_id", projectId), 1);
    jobList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    if (jobList.totalPage() > 0) {
      AbstractJob<?> lastJob = jobList.next().get(0);
      project.put("lastRunning", formater.format(lastJob.getCreatedDate()));
      project.put("log", true);
      project.put("lastSuites", lastJob.get("suites"));
      project.put("lastJobId", lastJob.getId());
    }
    
    return ok(Json.parse(project.toString()));
  }

  public Result create() {
    JsonNode json = request().body().asJson();
    String name = json.get("name").asText();
    boolean showAction = json.get("showAction").asBoolean();
    int valueDelay = json.get("valueDelay").asInt();
    KeywordProject project = keywordProjectFactory.create(context, name);
    project.setShowAction(showAction);
    project.setValueDelay(valueDelay);
    keywordProjectService.create(project);
    return status(201, project.getId());
  }
  
  public Result update() {
    JsonNode data = request().body().asJson();
    String id = data.get("id").asText();
    String name = data.get("name").asText();
    boolean showAction = data.get("showAction").asBoolean();
    int valueDelay = data.get("valueDelay").asInt();
    KeywordProject project = keywordProjectService.get(id,"show_action","value_delay");
    
    if (name.equals(project.getString("name"))) {
      if((project.getShowAction() == showAction) && (project.getValueDelay() == valueDelay)) {
        return status(304);
      }
    }
    project.put("name", name);
    project.setShowAction(showAction);
    project.setValueDelay(valueDelay);
    keywordProjectService.update(project);

    return status(202, id);
  }
  
  public Result delete() {
    
    String id = request().body().asText();
    
    KeywordProject project = keywordProjectService.get(id);
    if (project == null) {
      return status(404);
    }
    
    PageList<AbstractJob<?>> pages = executorService.query(new BasicDBObject("project_id", id));
    
    List<AbstractJob<?>> list = null;
    while (pages.hasNext()) {
      list = pages.next();
      for (AbstractJob<?> job : list) {
        reportService.deleteBy(new BasicDBObject("functional_job_id", job.getId()));
      }
    }
    
    executorService.deleteBy(new BasicDBObject("project_id", id));
    suiteService.deleteBy(new BasicDBObject("project_id", id));
    caseService.deleteBy(new BasicDBObject("project_id", id));
    customKeywordService.deleteBy(new BasicDBObject("project_id", id));
    keywordProjectService.delete(id);
    return status(200);
  }
  
  public Result run(String projectId) throws Exception {
    JsonNode data = request().body().asJson();
    JsonNode jsonSuites = data.get("suites");
    JsonNode jsonOptions = data.get("options");
    
    List<SuiteReference> suites = new ArrayList<SuiteReference>(jsonSuites.size());
    String versionSelenium = jsonOptions.get("versionSelenium") != null ? jsonOptions.get("versionSelenium").asText() : KeywordProjectFactory.DEFAULT_INIT_VERSION_SELENIUM;
    SuiteReference ref;
    for (JsonNode sel : jsonSuites) {
      ref = suiteRefFactory.create(sel.asText());
      
      if (suiteService.get(ref.getId()) == null) {
        return status(400);
      }
      
      String browser = jsonOptions.get("browser") != null ? jsonOptions.get("browser").asText() : null;
      String version = jsonOptions.get("version") != null ? jsonOptions.get("version").asText() : null;
      
      StringBuilder initDriver = new StringBuilder();
      if ("firefox".equals(browser)) {
        initDriver.append("System.setProperty(\"webdriver.firefox.bin\", \"/home/cloudats/firefox-store/").append(version).append("/firefox\");\n");
        initDriver.append("wd = new FirefoxDriver();");
      } else if ("chrome".equals(browser)) {
        initDriver.append("System.setProperty(\"webdriver.chrome.driver\", \"/home/cloudats/chromedriver\");\n wd = new ChromeDriver();");
      }
      
      if (initDriver.length() > 0) {
        Suite suite = ref.get();
        suite.put("init_driver", initDriver.toString());
        suiteService.update(suite);
      }
      
      suites.add(suiteRefFactory.create(sel.asText()));
    }
    
    KeywordProject project = keywordProjectService.get(projectId,"show_action","value_delay");
    if (project == null) return status(404);
    
    project.setVersionSelenium(versionSelenium);
    keywordProjectService.update(project);
    
    if (project.getStatus() == KeywordProject.Status.RUNNING) return status(204);
    
    KeywordJob job = executorService.execute(project, suites);
    return status(201, Json.parse(job.toString()));
  }
  
  public Result report(String projectId,String jobId) throws Exception{                    
    ArrayNode array = Json.newObject().arrayNode();
    
    AbstractJob<?> job = executorService.get(jobId);
    if(job.getRawDataOutput() != null) {
      PageList<Report> pages = reportService.getList(jobId, Type.FUNCTIONAL, null);
      
      if (pages.totalPage() <= 0) {
        return status(404);
      }
      
      Report report = pages.next().get(0);
      report.put("created_date", formater.format(job.getCreatedDate()));
      Iterator<SuiteReport> iterator = report.getSuiteReports().values().iterator();
      
      while (iterator.hasNext()) {
        SuiteReport suiteReport = iterator.next();
        Date date = suiteReport.getRunningTime();
        String parseDate = formater.format(date);
        suiteReport.put("running_time", parseDate);
        array.add(Json.parse(suiteReport.toString()));
        
      }
      
      report.put("suite_reports", array.toString());
      
      return status(200, Json.parse(report.toString()));
    }
    
    return status(404);
  }
  
  public Result listReport(String projectId) throws Exception {
    PageList<AbstractJob<?>> jobtList = executorService.query(new BasicDBObject("project_id", projectId), 1);
    jobtList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    ArrayNode array = Json.newObject().arrayNode();
    while(jobtList.hasNext()) {
      
      for(AbstractJob<?> job: jobtList.next()) {
        if(job.getRawDataOutput() != null) {
          ObjectNode obj = Json.newObject();
          
          PageList<Report> pages = reportService.getList(job.getId(), Type.FUNCTIONAL, null);
          Report report = pages.next().get(0);
          
          obj.put("report", Json.parse(report.toString()));
          obj.put("created_date", formater.format(job.getCreatedDate()));
          array.add(obj);
        }
      }
    }

    return ok(array);
  }
  
  public Result stopProject(String projectId) throws IOException {
    
    KeywordProject project = keywordProjectService.get(projectId);
    
    if (project == null) return status(404);
    
    VMachine jenkinsVM = vmachineService.getSystemVM(project.getTenant(), project.getSpace());
    
    PageList<AbstractJob<?>> jobList = executorService.query(new BasicDBObject("project_id", projectId), 1);
    jobList.setSortable(new MapBuilder<String, Boolean>("created_date", false).build());
    
    AbstractJob<?> lastJob = jobList.next().get(0);
    
    JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkinsVM.getPublicIp(), "http", "/jenkins", 8080);
    JenkinsMavenJob jenkinsJob = new JenkinsMavenJob(jenkinsMaster, lastJob.getId(), 
     null , null, null);
    
    lastJob.setStatus(AbstractJob.Status.Completed);
    executorService.update(lastJob);

    if (lastJob.getTestVMachineId() != null) {
      VMachine testVM = vmachineService.get(lastJob.getTestVMachineId());
      
      if (testVM.getStatus() == VMachine.Status.InProgress) {
        testVM.setStatus(VMachine.Status.Started);
        vmachineService.update(testVM);
      }
    } 
    
    project.setStatus(KeywordProject.Status.READY);
    keywordProjectService.update(project);
    
    jenkinsJob.stop();
    
    return status(200);
  }
  
  public Result download(String projectId, String jobId) {
    AbstractJob<?> absJob = executorService.get(jobId,"raw_data");
    String path = "/tmp/"+projectId.substring(0, 8);
    File folder = new File(path);
    if(!folder.exists()) {
      folder.mkdir();
    }
    KeywordJob job = (KeywordJob) absJob;
    if(job.getRawData() == null)
      return status(404);
    byte[] report = job.getRawData();
    FileOutputStream fileOut;
    try {
      fileOut = new FileOutputStream(path+"/resource-"+jobId+".tar.gz");
      fileOut.write(report);
      fileOut.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
     catch (IOException e) {
      e.printStackTrace();
    }
    
    response().setContentType("application/x-download");
    response().setHeader("Content-Encoding", "gzip");
    response().setHeader("Content-disposition",
        "attachment; filename=resource.tar.gz");
    return ok(new File(path+"/resource-"+jobId+".tar.gz"));
  }
}
