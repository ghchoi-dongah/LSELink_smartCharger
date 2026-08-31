package com.dongah.smartcharger.pages;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.os.Handler;
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

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AuthSelect4Fragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AuthSelect4Fragment extends Fragment implements View.OnClickListener {
    private static final Logger logger = LoggerFactory.getLogger(AuthSelect4Fragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    CardView cardViewMember, cardViewCorp, cardViewMoe, cardViewNoMember;
    TextView textViewMemberUnitInput, textViewCorpUnitInput, textViewMoeUnitInput, textViewNoMemberContent;

    MainActivity activity;
    ChargingCurrentData chargingCurrentData;
    Handler uiCheckHandler;

    public AuthSelect4Fragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment AuthSelect4Fragment.
     */
    // TODO: Rename and change types and number of parameters
    public static AuthSelect4Fragment newInstance(String param1, String param2) {
        AuthSelect4Fragment fragment = new AuthSelect4Fragment();
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
        View view = inflater.inflate(R.layout.fragment_auth_select4, container, false);
        activity = (MainActivity) MainActivity.mContext;
        chargingCurrentData = activity.getChargingCurrentData();

        cardViewMember = view.findViewById(R.id.cardViewMember);
        cardViewMember.setOnClickListener(this);
        cardViewCorp = view.findViewById(R.id.cardViewCorp);
        cardViewCorp.setOnClickListener(this);
        cardViewMoe = view.findViewById(R.id.cardViewMoe);
        cardViewMoe.setOnClickListener(this);
        cardViewNoMember = view.findViewById(R.id.cardViewNoMember);
        cardViewNoMember.setOnClickListener(this);

        textViewMemberUnitInput = view.findViewById(R.id.textViewMemberUnitInput);
        textViewCorpUnitInput = view.findViewById(R.id.textViewCorpUnitInput);
        textViewMoeUnitInput = view.findViewById(R.id.textViewMoeUnitInput);
        textViewNoMemberContent = view.findViewById(R.id.textViewNoMemberUnitInput);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            // 각 영역에 다른 색 적용
            setCardBorderColor(view.findViewById(R.id.layoutMoe),       R.color.green);
            setCardBorderColor(view.findViewById(R.id.layoutMember),    R.color.primary);
            setCardBorderColor(view.findViewById(R.id.layoutCorp),      R.color.blue_900);
            setCardBorderColor(view.findViewById(R.id.layoutNoMember),  R.color.yellow_900);

            textViewMemberUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeM));
            textViewCorpUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeC));
            textViewMoeUnitInput.setText(getString(R.string.price, GlobalVariables.userTypeK));
            textViewNoMemberContent.setText(getString(R.string.price, GlobalVariables.userTypeN));

            uiCheckHandler = new Handler();
            uiCheckHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    activity.getClassUiProcess().onHome();
                }
            }, 60000);
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClick(View v) {
        try {
            int id = v.getId();
            if (id == R.id.cardViewMember) {
                chargingCurrentData.setAuthType("M");
                chargingCurrentData.setPaymentType(PaymentType.MEMBER);
                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeM);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            } else if (id == R.id.cardViewNoMember) {
                Toast.makeText(getActivity(), "서비스 준비 중입니다.", Toast.LENGTH_SHORT).show();
//                chargingCurrentData.setAuthType("N");
//                chargingCurrentData.setPaymentType(PaymentType.CREDIT);
//                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeN);
//                activity.getClassUiProcess().setUiSeq(UiSeq.CREDIT_CARD);
//                activity.getFragmentChange().onFragmentChange(UiSeq.CREDIT_CARD, "CREDIT_CARD", null);
            }
            else if (id == R.id.cardViewCorp) {
                chargingCurrentData.setAuthType("C");
                chargingCurrentData.setPaymentType(PaymentType.CORP);
                chargingCurrentData.setPowerUnitPrice(GlobalVariables.userTypeC);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            } else if (id == R.id.cardViewMoe) {
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

    // stroke를 적용하는 헬퍼 메서드
    private void setCardBorderColor(View view, int colorRes) {
        LayerDrawable layerDrawable = (LayerDrawable) view.getBackground().mutate();
        GradientDrawable shape = (GradientDrawable) layerDrawable.getDrawable(1);
        shape.setStroke(2, ContextCompat.getColor(requireContext(), colorRes));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (uiCheckHandler != null) {
                uiCheckHandler.removeCallbacksAndMessages(null);
                uiCheckHandler = null;
            }
        } catch (Exception e) {
            logger.error("onDestroyView error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}