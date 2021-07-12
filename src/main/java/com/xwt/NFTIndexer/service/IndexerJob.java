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

public class IndexerJob implements Job {
    public void execute(final JobExecutionContext ctx) throws JobExecutionException {
        //
        // System.out.println will be used for testing purposes
        //

        Logger logger = Logger.getLogger(IndexerJob.class.getName());
        xrp4j lg = new xrp4j();
        long initialMarker;

        //Get current ledger index
        //initialMarker = lg.getledgerCI();
        initialMarker = 18999959l;
        boolean ledgerIsClosed = false;
        boolean loop = true;
        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
        do {
            System.out.println("Current ledger marker: " + initialMarker);
            do {
                try {
                    ledgerIsClosed = lg.getledgerResult(initialMarker).ledger().closed();
                } catch (JsonRpcClientErrorException e) {
                    e.printStackTrace();
                }
                if (ledgerIsClosed) {
                    System.out.println("Current ledger index is closed, proceeding...\n");
                } else {
                    System.out.println("Ledger is not closed waiting...\n");
                    TimeUtils.sleepFor(650, TimeUnit.MILLISECONDS);
                }
            } while (!ledgerIsClosed);

            List<TransactionResult<? extends Transaction>> getLedgerResult = null;
            try {
                getLedgerResult = lg.getledgerResult(initialMarker).ledger().transactions();
            } catch (JsonRpcClientErrorException e) {
                e.printStackTrace();
            }
            //Get ledger index transaction size
            int transactionSize = getLedgerResult.size();
            System.out.println("Current ledger transaction size: " + transactionSize);

            for (int i = 0; i < transactionSize; i++) {
                TransactionResult<? extends Transaction> transactionResult = getLedgerResult.get(i);
                if(transactionResult.transaction().transactionType().toString() == "ACCOUNT_SET"){
                    System.out.println(transactionResult.transaction());
                    String AccountAddress = transactionResult.transaction().account().toString();
                    try {
                        AccountRootObject accountInfo = lg.getInfo(AccountAddress, initialMarker);
                        byte[] s = DatatypeConverter.parseHexBinary(accountInfo.domain().get());
                        String domain = new String(s);
                        if(domain.startsWith("@xnft:")){
                            System.out.println("\n\nFound a NFT");
                            String[] parts = domain.split("\n");
                            for(int x = 1; x < parts.length; x++){
                                if(parts[x].subSequence(5,parts[x].length()).toString().startsWith("Qm")){
                                    try {
                                        InputStream ins = ipfs.catStream(Multihash.fromBase58(parts[x].subSequence(5, parts[x].length()).toString()));
                                        byte[] bytes = IOUtils.toByteArray(ins);
                                        String contentType = new Tika().detect(bytes);
                                        if(contentType.startsWith("image")){
                                            System.out.println("Image content type found...");
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                //If image convert it to base64
                                //else just get the IPFS Hash

                                //Add it to database
                            }
                        }

                    } catch (JsonRpcClientErrorException e) {
                        e.printStackTrace();
                    }

                }
                TimeUtils.sleepFor(650,TimeUnit.MILLISECONDS);
            }
            //initialMarker++;
        }while(loop);
    }
}
