package com.xwt.NFTIndexer;

import com.xwt.NFTIndexer.service.IndexerJob;
import okhttp3.HttpUrl;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.ledger.LedgerRequestParams;
import org.xrpl.xrpl4j.model.client.ledger.LedgerResult;
import org.xrpl.xrpl4j.model.ledger.AccountRootObject;
import org.xrpl.xrpl4j.model.transactions.Address;
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

public class xrp4j {
    final String URL = "https://s.altnet.rippletest.net:51234/";

    public XrplClient xrpclient(){
        return new XrplClient(HttpUrl.get(URL));
    }

    public Long getledgerCI() throws JsonRpcClientErrorException {
        LedgerResult ledgerResult = xrpclient().ledger(LedgerRequestParams.builder()
                .ledgerIndex(LedgerIndex.CURRENT)
                .build());
        return Long.valueOf(String.valueOf(ledgerResult.ledger().ledgerIndex()));
    }

    public LedgerResult getledgerResult(Long ledgerIndex) throws JsonRpcClientErrorException {
        return xrpclient().ledger(LedgerRequestParams.builder()
                .ledgerIndex(LedgerIndex.of(String.valueOf(ledgerIndex)))
                .transactions(true)
                .build());
    }

    public AccountRootObject getInfo(String account, Long index) throws JsonRpcClientErrorException {
        AccountInfoRequestParams params = AccountInfoRequestParams.builder()
                .account(Address.of(account))
                .ledgerIndex(LedgerIndex.of(String.valueOf(index)))
                .build();
        xrpclient().accountInfo(params);
        AccountInfoResult accountInfo = xrpclient().accountInfo(params);
        return accountInfo.accountData();
    }

    public static class WorkerJobLogic{
        public void indexerLogic() throws JsonRpcClientErrorException, IOException {
            Logger logger = Logger.getLogger(IndexerJob.class.getName());
            xrp4j lg = new xrp4j();

            long initialMarker;
            //initialMarker = lg.getledgerCI();
            initialMarker = 18999959l;

            boolean ledgerIsClosed = false;
            boolean loop = true;

            IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
            do {
                logger.info("Current ledger marker: " + initialMarker);
                do {
                        ledgerIsClosed = lg.getledgerResult(initialMarker).ledger().closed();
                    if (ledgerIsClosed) {
                        logger.info("Current ledger index is closed, proceeding...\n");
                    } else {
                        logger.info("Ledger is not closed waiting...\n");
                        TimeUtils.sleepFor(650, TimeUnit.MILLISECONDS);
                    }
                } while (!ledgerIsClosed);

                List<TransactionResult<? extends Transaction>> getLedgerResult = null;
                getLedgerResult = lg.getledgerResult(initialMarker).ledger().transactions();

                //Get ledger index transaction size
                int transactionSize = getLedgerResult.size();
                logger.info("Current ledger transaction size: " + transactionSize);

                for (int i = 0; i < transactionSize; i++) {
                    TransactionResult<? extends Transaction> transactionResult = getLedgerResult.get(i);
                    if(transactionResult.transaction().transactionType().toString() == "ACCOUNT_SET"){
                        logger.info(String.valueOf(transactionResult.transaction()));
                        String AccountAddress = transactionResult.transaction().account().toString();

                            AccountRootObject accountInfo = lg.getInfo(AccountAddress, initialMarker);
                            byte[] s = DatatypeConverter.parseHexBinary(accountInfo.domain().get());
                            String domain = new String(s);
                            if(domain.startsWith("@xnft:")){
                                logger.info("\n\nFound a NFT");
                                String[] parts = domain.split("\n");
                                for(int x = 1; x < parts.length; x++){
                                    if(parts[x].subSequence(5,parts[x].length()).toString().startsWith("Qm")){
                                            InputStream ins = ipfs.catStream(Multihash.fromBase58(parts[x].subSequence(5, parts[x].length()).toString()));
                                            byte[] bytes = IOUtils.toByteArray(ins);
                                            String contentType = new Tika().detect(bytes);
                                            if(contentType.startsWith("image")){
                                                logger.info("Image content type found...");
                                            }
                                    }
                                    //If image convert it to base64
                                    //else just get the IPFS Hash

                                    //Add it to database
                                }
                            }
                    }
                    TimeUtils.sleepFor(650,TimeUnit.MILLISECONDS);
                }
                //initialMarker++;
            }while(loop);

        }
    }
}
