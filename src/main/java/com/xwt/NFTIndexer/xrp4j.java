package com.xwt.NFTIndexer;

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
}
