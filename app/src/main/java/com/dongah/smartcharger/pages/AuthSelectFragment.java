package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.PaymentType;
import com.dongah.smartcharger.basefunction.UiSeq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthSelectFragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(AuthSelectFragment.class);

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    View rowBottom;
    View spacerLeft, spacerRight;
    View cardMember, cardNoMember, cardCorp, cardMoe;
    TextView textViewMemberUnitInput, textViewNoMemberUnitInput;
    TextView textViewCorporateUnitInput, textViewEnvironmentUnitInput;

    MainActivity activity;
    ChargingCurrentData chargingCurrentData;

    public AuthSelectFragment() {}

    public static AuthSelectFragment newInstance(String param1, String param2) {
        AuthSelectFragment fragment = new AuthSelectFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auth_select, container, false);

        rowBottom = view.findViewById(R.id.rowBottom);
        spacerLeft = view.findViewById(R.id.spacerLeft);
        spacerRight = view.findViewById(R.id.spacerRight);
        cardMember = view.findViewById(R.id.cardMember);
        cardNoMember = view.findViewById(R.id.cardNoMember);
        cardCorp = view.findViewById(R.id.cardCorp);
        cardMoe = view.findViewById(R.id.cardMoe);

        cardMember.setOnClickListener(this);
        cardNoMember.setOnClickListener(this);
        cardCorp.setOnClickListener(this);
        cardMoe.setOnClickListener(this);

        textViewMemberUnitInput = view.findViewById(R.id.textViewMemberUnitInput);
        textViewNoMemberUnitInput = view.findViewById(R.id.textViewNoMemberUnitInput);
        textViewCorporateUnitInput = view.findViewById(R.id.textViewCorporateUnitInput);
        textViewEnvironmentUnitInput = view.findViewById(R.id.textViewEnvironmentUnitInput);

        activity = (MainActivity) MainActivity.mContext;
        chargingCurrentData = activity.getChargingCurrentData();

        applyAuthMode(activity.getChargerConfiguration().getAuthMode());
        return view;
    }

    /**
     * authMode:
     *   1 → 회원(Q2) + 비회원(Q1)               : rowBottom GONE → 루트 gravity=center_vertical 로 rowTop 수직 중앙
     *   2 → 회원(Q2 전체) + 법인(Q3) + 환경부(Q4) : 비회원 GONE → 회원이 상단 전체 너비 차지
     *   3 → 회원(Q2) + 비회원(Q1) + 법인(Q3) + 환경부(Q4) : 4분면 모두 표시
     */
    private void applyAuthMode(int authMode) {
        switch (authMode) {
            case 1:
                spacerLeft.setVisibility(View.GONE);
                spacerRight.setVisibility(View.GONE);
                cardNoMember.setVisibility(View.VISIBLE);
                rowBottom.setVisibility(View.GONE);
                break;
            case 2:
                spacerLeft.setVisibility(View.VISIBLE);
                spacerRight.setVisibility(View.VISIBLE);
                cardNoMember.setVisibility(View.GONE);
                rowBottom.setVisibility(View.VISIBLE);
                break;
            case 3:
                spacerLeft.setVisibility(View.GONE);
                spacerRight.setVisibility(View.GONE);
                cardNoMember.setVisibility(View.VISIBLE);
                rowBottom.setVisibility(View.VISIBLE);
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            textViewMemberUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeM));
            textViewNoMemberUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeN));
            textViewCorporateUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeC));
            textViewEnvironmentUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeK));
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClick(View v) {
        try {
            int id = v.getId();
            if (id == R.id.cardMember) {
                chargingCurrentData.setAuthType("M");
                chargingCurrentData.setPaymentType(PaymentType.MEMBER);
                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeM);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            } else if (id == R.id.cardNoMember) {
                Toast.makeText(getActivity(), "서비스 준비 중입니다.", Toast.LENGTH_SHORT).show();
//                chargingCurrentData.setAuthType("N");
//                chargingCurrentData.setPaymentType(PaymentType.CREDIT);
//                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeN);
//                activity.getClassUiProcess().setUiSeq(UiSeq.CREDIT_CARD);
//                activity.getFragmentChange().onFragmentChange(UiSeq.CREDIT_CARD, "CREDIT_CARD", null);
            } else if (id == R.id.cardCorp) {
                chargingCurrentData.setAuthType("C");
                chargingCurrentData.setPaymentType(PaymentType.CORP);
                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeC);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            } else if (id == R.id.cardMoe) {
                chargingCurrentData.setAuthType("K");
                chargingCurrentData.setPaymentType(PaymentType.MOE);
                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeK);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            }
        } catch (Exception e) {
            logger.error("onClick error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
