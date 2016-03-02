/**
 * 
 */
package org.ats.services.executor.event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.ats.common.StringUtil;
import org.ats.common.ZipUtils;
import org.ats.jenkins.JenkinsMaster;
import org.ats.jenkins.JenkinsMavenJob;
import org.ats.services.event.Event;
import org.ats.services.event.EventFactory;
import org.ats.services.executor.ExecutorService;
import org.ats.services.executor.job.AbstractJob;
import org.ats.services.executor.job.AbstractJob.Status;
import org.ats.services.executor.job.KeywordJob;
import org.ats.services.executor.job.PerformanceJob;
import org.ats.services.executor.job.SeleniumUploadJob;
import org.ats.services.generator.GeneratorService;
import org.ats.services.iaas.IaaSServiceProvider;
import org.ats.services.keyword.KeywordProject;
import org.ats.services.keyword.KeywordProjectService;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.entity.reference.SpaceReference;
import org.ats.services.organization.entity.reference.TenantReference;
import org.ats.services.performance.JMeterScriptReference;
import org.ats.services.performance.JMeterScriptService;
import org.ats.services.performance.PerformanceProject;
import org.ats.services.performance.PerformanceProjectService;
import org.ats.services.upload.SeleniumUploadProject;
import org.ats.services.upload.SeleniumUploadProjectService;
import org.ats.services.vmachine.VMachine;
import org.ats.services.vmachine.VMachineReference;
import org.ats.services.vmachine.VMachineService;

import akka.actor.UntypedActor;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 23, 2015
 */
public class TrackingJobActor extends UntypedActor {

  @Inject ExecutorService executorService;
  
  @Inject GeneratorService generatorService;
  
  @Inject KeywordProjectService keywordService;
  
  @Inject PerformanceProjectService perfService;
  
  @Inject SeleniumUploadProjectService keywordUploadService;
  
  @Inject VMachineService vmachineService;
  
  @Inject IaaSServiceProvider iaasProvider;
  
  @Inject EventFactory eventFactory;
  
  @Inject Logger logger;
  
  @Inject ReferenceFactory<VMachineReference> vmRefFactory;
  
  @Inject JMeterScriptService jmeterService;
  
  ConcurrentHashMap<String, JenkinsMavenJob> cache = new ConcurrentHashMap<String, JenkinsMavenJob>();
  
  @Override
  public void onReceive(Object obj) throws Exception {
    if (obj instanceof Event) {
      Event event = (Event) obj;
      if ("keyword-job-tracking".equals(event.getName())) {
        KeywordJob job = (KeywordJob) event.getSource();
        processKeywordJob(job);
      } else if ("performance-job-tracking".equals(event.getName())) {
        PerformanceJob job = (PerformanceJob) event.getSource();
        processPerformanceJob(job);
      } else  if ("upload-job-tracking".equals(event.getName())) {
        SeleniumUploadJob job = (SeleniumUploadJob) event.getSource();
        processUploadJob(job);
      }
    }
  }

  private void processPerformanceJob(PerformanceJob job) throws Exception {
    try {
      PerformanceProject project = perfService.get(job.getProjectId());
      switch (job.getStatus()) {
      case Queued:
        doExecutePerformanceJob(job, project);
        break;
      case Running:
        doTrackingPerformanceJob(job, project);
        break;
      default:
        break;
      }
    } catch(Exception e) {
      job.setStatus(Status.Completed);
      executorService.update(job);
      
      VMachine vm = vmachineService.get(job.getTestVMachineId());
      if (vm != null) {
        vm.setStatus(VMachine.Status.Started);
        vmachineService.update(vm);
      }
      
      PerformanceProject project = perfService.get(job.getProjectId());
      project.setStatus(PerformanceProject.Status.READY);
      perfService.update(project);
      
      //bubble event
      Event event = eventFactory.create(job, "performance-job-tracking");
      event.broadcast();
      throw e;
    }
  }
  
  private void doTrackingPerformanceJob(PerformanceJob job, PerformanceProject project) throws Exception {
    
    TenantReference tenant = project.getTenant();
    SpaceReference space = project.getSpace();
    
    VMachine systemVM = vmachineService.getSystemVM(tenant, space);
    JenkinsMaster jkMaster = new JenkinsMaster(systemVM.getPublicIp(), "http", "jenkins", 8080);
    
    JenkinsMavenJob jkJob = cache.get(job.getId());
    if (jkJob == null) {
      jkJob = new JenkinsMavenJob(jkMaster, job.getId(), null, null, null);
      cache.put(job.getId(), jkJob);
    }
    
    boolean isBuilding = jkJob.isBuilding(1, System.currentTimeMillis(), 60 * 1000);
    
    if (isBuilding) {
      
      updateLog(job, jkJob);
      
      Thread.sleep(15000L);
      
      Event event  = eventFactory.create(job, "performance-job-tracking");
      event.broadcast();
      
    } else {
      
      updateLog(job, jkJob);
      
      //Download result
      BasicDBList list = new BasicDBList();
      for (JMeterScriptReference ref : job.getScripts()) {
        
        String report = StringUtil.readStream(new FileInputStream("/tmp/" + job.getId() + "/target/" + ref.getId() + ".jtl"));
        if (report.length() > 0) {
          list.add(new BasicDBObject("_id", ref.getId()).append("content", report));
        }
      }
      job.put("report", list);
      //End download result

      job.setStatus(AbstractJob.Status.Completed);
      executorService.update(job);

      //Reset test vm status and release floating ip
    //  VMachine testVM = vmachineService.get(job.getTestVMachineId());
      List<VMachineReference> pages = job.getVMs();
      for (VMachineReference ref : pages) {
        VMachine testVM = ref.get();
        testVM.setStatus(VMachine.Status.Started);
        vmachineService.update(testVM);
      }
      //testVM.setStatus(VMachine.Status.Started);
      //vmachineService.update(testVM);
      
      project.setStatus(PerformanceProject.Status.READY);
      perfService.update(project);
      
      jkJob.delete();
      cache.remove(job.getId());
      
      Event event  = eventFactory.create(job, "performance-job-tracking");
      event.broadcast();
    }
  }
  
  private void doExecutePerformanceJob(PerformanceJob job, PerformanceProject project) throws Exception {
    
    VMachine jenkinsVM = vmachineService.getSystemVM(project.getTenant(), project.getSpace());
    updateLog(job, "Generating performance project");
    generatorService.generatePerformance("/tmp", job.getId(), false, job.getScripts());

    List<JMeterScriptReference> listScript = job.getScripts();
    StringBuilder goalsBuilder = new StringBuilder("clean test ");
    for (JMeterScriptReference ref : listScript) {

      goalsBuilder.append("-Djmeter.").append(ref.getId()).append("=");
      goalsBuilder.append(jenkinsVM.getPrivateIp());
      job.addVMachine(vmRefFactory.create(jenkinsVM.getId()));
      jenkinsVM.setStatus(VMachine.Status.InProgress);
      vmachineService.update(jenkinsVM);

      goalsBuilder.append(" ");
    }

    JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkinsVM.getPublicIp(), "http", "jenkins", 8080);
    JenkinsMavenJob jenkinsJob = new JenkinsMavenJob(jenkinsMaster, job.getId(), 
        "master" , "/tmp/" + job.getId() + "/pom.xml", goalsBuilder.toString());

    jenkinsJob.submit();
    updateLog(job, "Submitted Jenkins job");

    job.setStatus(Status.Running);

    executorService.update(job);

    Event event  = eventFactory.create(job, "performance-job-tracking");
    event.broadcast();
  }

  private void processKeywordJob(KeywordJob job) throws Exception {
    try {
      KeywordProject project = keywordService.get(job.getProjectId(),"show_action","value_delay","version_selenium");
      switch (job.getStatus()) {
      case Queued:
        doExecuteKeywordJob(job, project);
        break;
      case Running:
        doTrackingKeywordJob(job, project);
        break;
      default:
        break;
      }
    } catch (Exception e) {
      job.setStatus(Status.Completed);
      executorService.update(job);
      
      VMachine vm = vmachineService.get(job.getTestVMachineId());
      if (vm != null) {
        vm.setStatus(VMachine.Status.Started);
        vmachineService.update(vm);
      }
      
      KeywordProject project = keywordService.get(job.getProjectId());
      project.setStatus(KeywordProject.Status.READY);
      keywordService.update(project);
      
      //bubble event
      Event event = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
      throw e;
    }
  }
  
  private void doTrackingKeywordJob(KeywordJob job, KeywordProject project) throws Exception {
    
    TenantReference tenant = project.getTenant();
    SpaceReference space = project.getSpace();
    
    VMachine systemVM = vmachineService.getSystemVM(tenant, space);
    VMachine testVM = vmachineService.get(job.getTestVMachineId());
    
    JenkinsMaster jkMaster = new JenkinsMaster(systemVM.getPublicIp(), "http", "jenkins", 8080);
    JenkinsMavenJob jkJob = cache.get(job.getId());
    if (jkJob == null) {
      jkJob = new JenkinsMavenJob(jkMaster, job.getId(), null, null, null);
      cache.put(job.getId(), jkJob);
    }
    
    boolean isBuilding = jkJob.isBuilding(1, System.currentTimeMillis(), 60 * 1000);
    if (isBuilding) {
      
      updateLog(job, jkJob);
      
      Thread.sleep(5000L);
      
      Event event  = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
      
    } else {
      updateLog(job, jkJob);
      
      //Download target resource
      
      
      //Download result
      String report = StringUtil.readStream(new FileInputStream("/tmp/" + job.getId() + "/target/surefire-reports/testng-results.xml"));
      if (report.length() > 0)
        job.put("report", report);
      //End download result

      job.setStatus(AbstractJob.Status.Completed);
      executorService.update(job);
      
      //Reset vm status and release floating ip
      testVM.setStatus(VMachine.Status.Started);
      vmachineService.update(testVM);
      
      //TODO:Need review it
      // testVM = openstackService.deallocateFloatingIp(testVM);
      
      project.setStatus(KeywordProject.Status.READY);
      keywordService.update(project);
      
      jkJob.delete();
      cache.remove(job.getId());
      
      Event event  = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
    }
  }
  
  private void doExecuteKeywordJob(KeywordJob job, KeywordProject project) throws Exception {

    VMachine jenkinsVM = vmachineService.getSystemVM(project.getTenant(), project.getSpace());

    if (jenkinsVM.getStatus() == VMachine.Status.Started) {
      updateLog(job, "Generating keyword project");
      generatorService.generateKeyword("/tmp", job.getId(), false, job.getSuites(), project.getShowAction(), project.getValueDelay(), project.getVersionSelenium());

      StringBuilder goalsBuilder = new StringBuilder("clean test");

      JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkinsVM.getPublicIp(), "http", "jenkins", 8080);
      JenkinsMavenJob jenkinsJob = new JenkinsMavenJob(jenkinsMaster, job.getId(), 
          "master" , "/tmp/" + job.getId() + "/pom.xml", goalsBuilder.toString());

      jenkinsJob.submit();
      updateLog(job, "Submitted Jenkins job");

      jenkinsVM.setStatus(VMachine.Status.InProgress);
      vmachineService.update(jenkinsVM);

      job.setStatus(Status.Running);
      job.setVMachineId(jenkinsVM.getId());
      executorService.update(job);
      
      Event event  = eventFactory.create(job, "keyword-job-tracking");
      event.broadcast();
    }
  }
  
  private void processUploadJob(SeleniumUploadJob job) throws Exception {
    try {
      SeleniumUploadProject project = keywordUploadService.get(job.getProjectId(),"raw");
      switch (job.getStatus()) {
      case Queued:
        doExecuteUploadJob(job, project);
        break;
      case Running:
        doTrackingUploadJob(job, project);
        break;
      default:
        break;
      }
    } catch (Exception e) {
      job.setStatus(Status.Completed);
      executorService.update(job);
      
      VMachine vm = vmachineService.get(job.getTestVMachineId());
      if (vm != null) {
        vm.setStatus(VMachine.Status.Started);
        if (vm.getPublicIp() != null) iaasProvider.get().deallocateFloatingIp(vm);
        else vmachineService.update(vm);
      }
      
      SeleniumUploadProject project = keywordUploadService.get(job.getProjectId(),"raw");
      project.setStatus(SeleniumUploadProject.Status.READY);
      keywordUploadService.update(project);
      
      //bubble event
      Event event = eventFactory.create(job, "upload-job-tracking");
      event.broadcast();
      throw e;
    }
  }
  
private void doTrackingUploadJob(SeleniumUploadJob job, SeleniumUploadProject project) throws Exception {
    TenantReference tenant = project.getTenant();
    SpaceReference space = project.getSpace();
    
    VMachine systemVM = vmachineService.getSystemVM(tenant, space);
    VMachine testVM = vmachineService.get(job.getTestVMachineId());
    
    JenkinsMaster jkMaster = new JenkinsMaster(systemVM.getPublicIp(), "http", "jenkins", 8080);
    JenkinsMavenJob jkJob = cache.get(job.getId());
    if (jkJob == null) {
      jkJob = new JenkinsMavenJob(jkMaster, job.getId(), null, null, null);
      cache.put(job.getId(), jkJob);
    }
    
    boolean isBuilding = jkJob.isBuilding(1, System.currentTimeMillis(), 60 * 1000);
    if (isBuilding) {
      
      updateLog(job, jkJob);
      
      Thread.sleep(5000L);
      
      Event event = eventFactory.create(job, "upload-job-tracking");
      event.broadcast();
      
    } else {
      updateLog(job, jkJob);
      
      //Download result
      
      //Zip report
        job.put("result", jkJob.getStatus(1));
      //End download result

      job.setStatus(AbstractJob.Status.Completed);
      executorService.update(job);
      
      //Reset vm status and release floating ip
      testVM.setStatus(VMachine.Status.Started);
      vmachineService.update(testVM);
      
      project.setStatus(SeleniumUploadProject.Status.READY);
      keywordUploadService.update(project);
      
      jkJob.delete();
      cache.remove(job.getId());
      
      Event event = eventFactory.create(job, "upload-job-tracking");
      event.broadcast();
    }
  }
  
  private void doExecuteUploadJob(SeleniumUploadJob job, SeleniumUploadProject project) throws Exception {

    VMachine jenkinsVM = vmachineService.getSystemVM(project.getTenant(), project.getSpace());
        
    if (jenkinsVM.getStatus() == VMachine.Status.Started) {
      byte [] bFile = project.getRawData();
      String fileName = job.getId();
      String path = "/tmp/" + fileName + ".zip";
      FileOutputStream fileOut = new FileOutputStream(path);
      fileOut.write(bFile);
      fileOut.close();
      
      File out = new File("/tmp/" + fileName);
      out.mkdirs();
      ZipUtils.extract(new File(path), out);
      
      JenkinsMaster jenkinsMaster = new JenkinsMaster(jenkinsVM.getPublicIp(), "http", "/jenkins", 8080);
      JenkinsMavenJob jenkinsJob = new JenkinsMavenJob(jenkinsMaster, job.getId(), "master" , "/tmp/" + fileName + "/pom.xml", "clean test");
      jenkinsJob.submit();
      updateLog(job, "Submitted Jenkins job");
      
      jenkinsVM.setStatus(VMachine.Status.InProgress);
      vmachineService.update(jenkinsVM);

      job.setStatus(Status.Running);
      job.setVMachineId(jenkinsVM.getId());
      executorService.update(job);
      
      Event event = eventFactory.create(job, "upload-job-tracking");
      event.broadcast();
    }
  }
  
  private void updateLog(AbstractJob<?> job, String log) {
    StringBuilder sb = new StringBuilder("[").append(new Date()).append("]").append("[INFO] ");
    job.appendLog(sb.append(log).append("\n").toString());
    executorService.update(job);
  }
  
  private void updateLog(AbstractJob<?> job, JenkinsMavenJob jkJob) throws IOException {
    int start = job.getLog() == null ? 0 : job.getLog().length();
    if (job.getLog() != null) {
      int _index = job.getLog().indexOf("Submitted Jenkins job");
      if (_index != -1) {
        _index += "Submitted Jenkins job\n".length();
        start -= _index;
      }
    }
    byte[] bytes = jkJob.getConsoleOutput(1, start); 
    byte[] next = new byte[bytes.length - start];
    System.arraycopy(bytes, start, next, 0, next.length);
    if (next.length > 0) {
      job.appendLog(new String(next));
      executorService.update(job);
    }
  }
}
