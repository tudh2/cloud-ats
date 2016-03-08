/**
 * 
 */
package controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.ats.common.PageList;
import org.ats.services.OrganizationContext;
import org.ats.services.datadriven.DataDriven;
import org.ats.services.datadriven.DataDrivenFactory;
import org.ats.services.datadriven.DataDrivenReference;
import org.ats.services.datadriven.DataDrivenService;
import org.ats.services.keyword.Case;
import org.ats.services.keyword.CaseService;
import org.ats.services.organization.SpaceService;
import org.ats.services.organization.acl.Authenticated;
import org.ats.services.organization.entity.Tenant;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.entity.reference.SpaceReference;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import actions.CorsComposition;
import au.com.bytecode.opencsv.CSVReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * May 21, 2015
 */
@CorsComposition.Cors
@Authenticated
public class DataDrivenController extends Controller {
  
  @Inject
  OrganizationContext context;
  
  @Inject
  SpaceService spaceService;
  
  @Inject
  ReferenceFactory<SpaceReference> spaceRefFactory;
  
  @Inject
  DataDrivenService dataDrivenService;
  
  @Inject
  DataDrivenFactory dataDrivenFactory;
  
  @Inject
  ReferenceFactory<DataDrivenReference> dataRefFactory;
  
  @Inject
  CaseService caseService;
  
  public Result list() {
    
    Tenant currentTenant = context.getTenant();
    PageList<DataDriven> pages = dataDrivenService.query(new BasicDBObject("tenant", new BasicDBObject("_id", currentTenant.getId())));
    ArrayNode arrayData = Json.newObject().arrayNode();
    
    while (pages.hasNext()) {
      List<DataDriven> list = pages.next();
      
      for (DataDriven data : list) {
    	ObjectNode obj = Json.newObject();
    	obj.put("_id", data.getId());
    	obj.put("name", data.getName());
        arrayData.add(obj);
      }
    }
    return status(200, arrayData);
  }
  
  public Result create() {
    JsonNode json = request().body().asJson();
    String name = json.get("name").asText();
    String caseId = json.get("caseId").asText();
    JsonNode dataset = json.get("dataset");
    DataDriven driven = dataDrivenFactory.create(name, dataset.toString());
    dataDrivenService.create(driven);
    
    if ("null".equals(caseId)) {
      return status(200, Json.parse(driven.toString()));
    }
    
    String drivenId = driven.getId();
    Case caze = caseService.get(caseId);
    
    caze.setDataDriven(dataRefFactory.create(drivenId));
    caseService.update(caze);
    return ok(Json.parse(driven.toString()));
  }
  
  public Result get(String id) {
    
    DataDriven driven = dataDrivenService.get(id);
    ObjectNode json = Json.newObject();
    
    json.put("data", Json.parse(driven.toString()));
    
    return ok(json);
  }
  
  public Result delete() {
    
    String id = request().body().asText();
    DataDrivenReference dataRef = dataRefFactory.create(id);
    
    PageList<Case> pages = caseService.query(new BasicDBObject("data_driven", dataRef.toJSon()));
    while (pages.hasNext()) {
      List<Case> list = pages.next();
      for (Case caze : list) {
        caze.setDataDriven(null);
        caseService.update(caze);
      }
    }
    
    dataDrivenService.delete(id);
    
    return status(200);
  }
  
  public Result update() {
    JsonNode json = request().body().asJson();
    String id = json.get("id").asText();
    JsonNode dataset = json.get("dataset");
    DataDriven driven = dataDrivenService.get(id);
    String name = json.get("name").asText();
    if (dataset.toString().equals(driven.getDataSource()) && name.equals(driven.getName())) {
      return status(304);
    }
    driven.put("name", name);
    driven.setDataSource(dataset.toString());
    dataDrivenService.update(driven);
    
    return status(200, Json.parse(driven.toString()));
  }
  
  public Result upload(String projectId,String caseId) throws FileNotFoundException {
    
    if ("null".equals(projectId)) {
      MultipartFormData body = request().body().asMultipartFormData();
      MultipartFormData.FilePart typeFile = body.getFile("file");
      String fileName = typeFile.getFilename();
      File file = typeFile.getFile();
      
      BufferedReader br = new BufferedReader(new FileReader(file));
      
      ArrayNode array = readBufferByOpenCSV(br);
      DataDriven data = dataDrivenFactory.create(fileName, array.toString());
      dataDrivenService.create(data);
      
      return ok(Json.parse(data.toString()));
    }
    //Get file csv upload
    MultipartFormData body = request().body().asMultipartFormData();
    MultipartFormData.FilePart typeFile = body.getFile("file");
    DataDriven dataDriven = null ;
    if(typeFile != null) {
      File file = typeFile.getFile();
      BufferedReader br = null;
      String fullName = typeFile.getFilename();
      int lengthName = fullName.split("\\.").length;
      String fileName = typeFile.getFilename().split("\\.")[lengthName-2];
    
      //Get params of data driven
      JsonNode paramsNode = Json.parse(body.asFormUrlEncoded().get("params")[0]);
      List<String> listParams;
      listParams = new ArrayList<String>();
      Iterator<JsonNode> iterator = paramsNode.iterator();
      String line = "";
      
      while(iterator.hasNext()) {
        JsonNode element = iterator.next();
        String param = element.get("param").toString().split("\"")[1];
        listParams.add(param);
      }
      
      try {
        br = new BufferedReader(new FileReader(file));
        int count = listParams.size();
        int numberOfLine = -1;
        ObjectNode obj = null;
        ArrayNode dataset = Json.newObject().arrayNode();
        while((line = br.readLine()) != null) {
          String contentLine [] = line.split(",");
          
          numberOfLine ++;
          //Except the first line which contain name of params
          if(numberOfLine == 0) continue;
          
          obj = Json.newObject();
          for(int i = 0; i < count; i++) {
            String key = listParams.get(i);
            String value = contentLine[i];
            obj.put(key, value);
          }
          dataset.add(obj);
        }
        dataDriven = dataDrivenFactory.create(fileName, dataset.toString());
        dataDrivenService.create(dataDriven);
        
        if ("null".equals(caseId)) {
          return status(200, Json.parse(dataDriven.toString()));
        }
        
        String drivenId = dataDriven.getId();
        Case caze = caseService.get(caseId);
        
        if(caze.getDataDriven() != null) {
          caze.setDataDriven(null);
        }
        
        caze.setDataDriven(dataRefFactory.create(drivenId));
        caseService.update(caze);
        
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (br != null) {
          try {
            br.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
    return ok(Json.parse(dataDriven.toString()));
  }
  @SuppressWarnings("resource")
  private ArrayNode readBufferByOpenCSV(BufferedReader br) {
    ArrayNode array = null;
    try {
      array = Json.newObject().arrayNode();
      CSVReader reader = new CSVReader(br);
      String[] nextLine;
      String[] firstLine = reader.readNext();
      while ((nextLine = reader.readNext()) != null) {
        ObjectNode object = Json.newObject();
        for (int i = 0; i < nextLine.length; i++) {
          object.put(firstLine[i], nextLine[i]);
        }
        array.add(object);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return array;

  }
  
}

