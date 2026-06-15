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

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.PaymentType;
import com.dongah.smartcharger.basefunction.UiSeq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AuthSelectFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AuthSelectFragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(AuthSelectFragment.class);


    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    View viewMember, viewNoMember;
    TextView textViewMemberUnitInput, textViewNoMemberUnitInput;

    MainActivity activity;
    ChargingCurrentData chargingCurrentData;

    public AuthSelectFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment AuthFragment.
     */
    // TODO: Rename and change types and number of parameters
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auth_select, container, false);
        viewMember = view.findViewById(R.id.viewMember);
        viewMember.setOnClickListener(this);
        viewNoMember = view.findViewById(R.id.viewNoMember);
        viewNoMember.setOnClickListener(this);
        textViewMemberUnitInput = view.findViewById(R.id.textViewMemberUnitInput);
        textViewNoMemberUnitInput = view.findViewById(R.id.textViewNoMemberUnitInput);

        activity = (MainActivity) MainActivity.mContext;
        chargingCurrentData = activity.getChargingCurrentData();
        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            textViewMemberUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeM));
            textViewNoMemberUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeN));
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClick(View v) {
        try {
            int getId = v.getId();
            if (Objects.equals(getId, R.id.viewMember)) {
                chargingCurrentData.setAuthType("M");
                chargingCurrentData.setPaymentType(PaymentType.MEMBER);
                chargingCurrentData.setUnitPrice(GlobalVariables.userTypeM);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            } else if (Objects.equals(getId, R.id.viewNoMember)) {
                chargingCurrentData.setAuthType("N");
                chargingCurrentData.setPaymentType(PaymentType.CREDIT);
                chargingCurrentData.setUnitPrice(GlobalVariables.userTypeN);
                activity.getClassUiProcess().setUiSeq(UiSeq.CREDIT_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.CREDIT_CARD, "CREDIT_CARD", null);
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