package com.xwt.NFTIndexer;

import com.mysql.cj.protocol.Resultset;
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
import io.ipfs.api.IPFS;
import io.ipfs.multihash.Multihash;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.transactions.Transaction;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
        private Logger logger = Logger.getLogger(IndexerJob.class.getName());
        private IPFS ipfs = new IPFS(System.getenv("IPFS_Multiaddress"));
        private xrp4j lg = new xrp4j();
        private xrp4j.DAL dal = new xrp4j.DAL();

        private long initialMarker;

        private boolean ledgerIsClosed = false;
        private boolean loop = true;
        private String ipfsImage;

        public void indexerLogic() throws JsonRpcClientErrorException, IOException, SQLException, IllegalAccessException, ClassNotFoundException, InstantiationException {
            //initialMarker = 18999959l; -- Ledger# with XLS19d
            initialMarker = lg.getledgerCI();
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

                //Get ledger raw data
                List<TransactionResult<? extends Transaction>> getLedgerResult;
                getLedgerResult = lg.getledgerResult(initialMarker).ledger().transactions();

                //Get ledger index transaction size
                int transactionSize = getLedgerResult.size();
                logger.info("Current ledger transaction size: " + transactionSize);

                //Scan for "ACCOUNT_SET" in ledger transaction
                for (int i = 0; i < transactionSize; i++) {
                    TransactionResult<? extends Transaction> transactionResult = getLedgerResult.get(i);
                    //If ACCOUNT_SET domain transaction is found use ledger transaction address to get more data.
                    if(transactionResult.transaction().transactionType().toString() == "ACCOUNT_SET"){
                        logger.info(String.valueOf(transactionResult.transaction()));
                        String AccountAddress = transactionResult.transaction().account().toString();
                            //Get AccountRootObject by using ledger transaction address.
                            AccountRootObject accountInfo = lg.getInfo(AccountAddress, initialMarker);
                            //Convert domain hex
                            byte[] s = DatatypeConverter.parseHexBinary(accountInfo.domain().get());
                            //Make 's' readable.
                            String domain = new String(s);
                            if(domain.startsWith("@xnft:")){
                                logger.info("Found a NFT\n");
                                //Split the domain parts into the array
                                String[] parts = domain.split("\n");
                                for(int x = 1; x < parts.length; x++){
                                    if(parts[x].subSequence(5,parts[x].length()).toString().startsWith("Qm")){
                                            InputStream ins = ipfs.catStream(Multihash.fromBase58(parts[x].subSequence(5, parts[x].length()).toString()));
                                            byte[] bytes = IOUtils.toByteArray(ins);
                                            String contentType = new Tika().detect(bytes);
                                            if(contentType.startsWith("image")){
                                                logger.info("Image content type found...");
                                                ipfsImage = ("https://gateway.pinata.cloud/ipfs/"+parts[x].subSequence(5, parts[x].length()).toString());
                                                String PK = String.valueOf(dal.insertLedger(String.valueOf(initialMarker)));
                                                logger.info("DB Inserted PK: "+ PK);
                                                //Insert compiled data to database
                                                dal.insertNFTData(PK, AccountAddress, ipfsImage);
                                            }
                                    }
                                }
                            }
                    }
                    TimeUtils.sleepFor(650,TimeUnit.MILLISECONDS);
                }
                initialMarker++;
            }while(loop);
        }
    }
    public static class DAL{
        private Logger logger = Logger.getLogger(DAL.class.getName());
        private final String userName = System.getenv("dbUsername");
        private final String password = System.getenv("dbPassword");
        private final String endpoint = System.getenv("dbEndpoint");

        private Connection getConnection() throws SQLException  {
            DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
            Connection connection = DriverManager.getConnection(endpoint, userName, password);
            return connection;
        }

        public int insertNFTData(String id, String classicAddress, String domainValue) throws SQLException, ClassNotFoundException {
            Connection connection = getConnection();
            String SQLQuery = "INSERT INTO indexer.caIndexed (id, classicAddress, domainValue) VALUES (?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(SQLQuery);
            stmt.setString(1, id);
            stmt.setString(2, classicAddress);
            stmt.setString(3, domainValue);
            return stmt.executeUpdate();
        }

        public int insertLedger(String ledgerIndex) throws SQLException, ClassNotFoundException {
            int key = 0;
            Connection connection = getConnection();
            String SQLQuery = "INSERT INTO indexer.indexer (ledgerIndex) VALUES (?)";
            PreparedStatement stmt = connection.prepareStatement(SQLQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, ledgerIndex);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            while(rs.next()){
                key = rs.getInt(1);
            }
            return key;
        }
    }
}
