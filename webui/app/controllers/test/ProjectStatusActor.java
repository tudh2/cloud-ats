/**
 * 
 */
package controllers.test;

import helpertest.JMeterScriptHelper;
import helpertest.JenkinsJobExecutor;
import helpertest.JenkinsJobHelper;
import helpertest.TestHelper;
import helpervm.VMHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.ats.component.usersmgt.group.Group;
import org.ats.jmeter.models.JMeterScript;

import models.test.JenkinsJobModel;
import models.test.TestProjectModel;
import models.test.TestProjectModel.TestProjectType;
import models.vm.VMModel.VMStatus;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;

import controllers.vm.VMCreator;
import play.Logger;
import play.libs.Akka;
import play.libs.Json;
import scala.concurrent.duration.Duration;
import utils.LogBuilder;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Oct 27, 2014
 */
public class ProjectStatusActor extends UntypedActor {

  static ActorRef actor = Akka.system().actorOf(Props.create(ProjectStatusActor.class));
  
  final static Cancellable canceller = Akka.system().scheduler().schedule(Duration.create(100, TimeUnit.MILLISECONDS), Duration.create(1, TimeUnit.SECONDS),
      actor, "Check", Akka.system().dispatcher(), null);
  
  Map<String, ProjectChannel> channels = new HashMap<String, ProjectChannel>();
  
  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof ProjectChannel) {
      ProjectChannel channel = (ProjectChannel) msg;
      channels.put(channel.sessionId, channel);
      getSender().tell("OK", getSelf());
    } else if (msg.equals("Check")) {
      for (ProjectChannel channel : channels.values()) {
        
        ObjectNode jsonObj = Json.newObject();
        ArrayNode arrayStatus = jsonObj.arrayNode();
        ArrayNode arrayLogs = jsonObj.arrayNode();
        
        Set<TestProjectModel> projects = new HashSet<TestProjectModel>();
        for (Group group : TestController.getAvailableGroups(channel.userId)) {
          projects.addAll(TestHelper.getProject(TestProjectType.valueOf(channel.type), new BasicDBObject("group_id", group.getId())));
        }
        
        for (TestProjectModel project : projects) {

          ObjectNode projectNode = Json.newObject().put("id", project.getId()).put("status", project.getStatus()).put("last_build", project.getLastBuildDate());
              
          switch (project.getType()) {
          case performance:
            ArrayNode snapshotNode = projectNode.arrayNode();
            for (JMeterScript snapshot : JMeterScriptHelper.getJMeterScript(project.getId())) {
              snapshotNode.add(Json.newObject().put("id", snapshot.getString("_id")).put("status", snapshot.getString("status")).put("last_build", snapshot.getLastBuildDate()));
            }
            projectNode.put("snapshots", snapshotNode);
          case functional:
            arrayStatus.add(projectNode);
            break;
          default:
            break;
          }
        }
        
        List<JenkinsJobModel> jobs = JenkinsJobHelper.getRunningJobs();
        for (JenkinsJobModel job : jobs) {
          TestProjectModel project = TestHelper.getProjectById(TestProjectType.valueOf(channel.type), job.getString("project_id"));
          if (!projects.contains(project)) continue;
          
          
          ConcurrentLinkedQueue<String> queue = JenkinsJobExecutor.QueueHolder.get(job.getId());
          if (queue == null) continue;
          String s = queue.poll();
          
          if (s != null) {
            //push to log
            StringBuilder sb = job.getString("log") == null ? new StringBuilder() : new StringBuilder(job.getString("log")).append("\n");
            LogBuilder.log(sb, s);
            job.put("log", sb.toString());
            System.out.println(job);
            JenkinsJobHelper.updateJenkinsJob(job);
            
            arrayLogs.add(Json.newObject().put("id", job.getId()).put("msg", s));
            Logger.debug(s);
          }
          if ("log.exit".equals(s)) {
            JenkinsJobExecutor.QueueHolder.remove(job.getId());
          }
        }
        
        jsonObj.put("projects", arrayStatus);
        jsonObj.put("logs", arrayLogs);
        channel.out.write(jsonObj);
      }
    } else {
      unhandled(msg);
    }
  }

}