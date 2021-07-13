package com.xwt.NFTIndexer.service;

import com.xwt.NFTIndexer.xrp4j;
import com.xwt.NFTIndexer.xrp4j.WorkerJobLogic;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;

import java.io.IOException;
import java.sql.SQLException;

public class IndexerJob implements Job {
    public void execute(final JobExecutionContext ctx) throws JobExecutionException {
        xrp4j.WorkerJobLogic wjl = new WorkerJobLogic();

        try {
            wjl.indexerLogic();
        } catch (JsonRpcClientErrorException | IOException | SQLException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }
}
