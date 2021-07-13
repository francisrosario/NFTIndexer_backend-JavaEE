package com.xwt.NFTIndexer.service;

import com.mgnt.utils.TimeUtils;
import com.xwt.NFTIndexer.xrp4j;
import io.ipfs.api.IPFS;
import io.ipfs.multihash.Multihash;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.bouncycastle.util.encoders.UTF8;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.ledger.AccountRootObject;
import org.xrpl.xrpl4j.model.transactions.Transaction;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.xwt.NFTIndexer.xrp4j.WorkerJobLogic;

public class IndexerJob implements Job {
    public void execute(final JobExecutionContext ctx) throws JobExecutionException {
        xrp4j.WorkerJobLogic wjl = new WorkerJobLogic();

        try {
            wjl.indexerLogic();
        } catch (JsonRpcClientErrorException | IOException e) {
            e.printStackTrace();
        }

    }
}
