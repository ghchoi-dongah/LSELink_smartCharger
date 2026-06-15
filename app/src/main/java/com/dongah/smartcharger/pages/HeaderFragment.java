package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.utils.SharedModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HeaderFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HeaderFragment extends Fragment implements View.OnClickListener {

    private static final Logger logger = LoggerFactory.getLogger(HeaderFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    int clickedCnt = 0;
    ImageButton btnHome, btnLogo;
    TextView textViewChargerId;
    MainActivity activity;
    ChargerConfiguration chargerConfiguration;
    SharedModel sharedModel;

    public HeaderFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment HeaderFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HeaderFragment newInstance(String param1, String param2) {
        HeaderFragment fragment = new HeaderFragment();
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

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_header, container, false);
        activity = (MainActivity) MainActivity.mContext; 
        btnHome = view.findViewById(R.id.btnHome);
        btnHome.setOnClickListener(this);
        btnLogo = view.findViewById(R.id.btnLogo);
        btnLogo.setOnClickListener(this);
        textViewChargerId = view.findViewById(R.id.textViewChargerId);

        try {
            chargerConfiguration = activity.getChargerConfiguration();
            textViewChargerId.setText("| ID-" + chargerConfiguration.getChargerId());
        } catch (Exception e) {
            logger.error("onCreateView error : {}", e.getMessage());
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            sharedModel = new ViewModelProvider(requireActivity()).get(SharedModel.class);
            sharedModel.getLiveData().observe(getViewLifecycleOwner(), new Observer<String[]>() {
                @Override
                public void onChanged(String[] strings) {
                    UiSeq uiSeq = activity.getClassUiProcess().getUiSeq();
                    switch (uiSeq) {
                        case MEMBER_CARD:
                        case MEMBER_CHECK_WAIT:
                        case CREDIT_CARD_WAIT:
                        case CHARGING:
                        case PLUG_CHECK:
                        case CONNECT_CHECK:
                        case FAULT:
                        case REBOOTING:
                            btnHome.setVisibility(View.INVISIBLE);
                            break;
                        default:
                            btnHome.setVisibility(View.VISIBLE);
                            break;
                    }
                }
            });
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClick(View v) {
        if (Objects.equals(v.getId(), R.id.btnLogo)) {
            System.out.println("btnLogo click: " + clickedCnt);
            if (clickedCnt > 8) {
                try {
                    if (activity == null) {
                        System.out.println("btnLogo error: MainActivity.mContext is null");
                        return;
                    }

                    UiSeq ui = activity.getClassUiProcess() != null
                            ? activity.getClassUiProcess().getUiSeq()
                            : null;

                    boolean chkUiSeq = (ui == UiSeq.INIT || ui == UiSeq.FAULT || ui == UiSeq.OP_STOP);
                    System.out.println("clickedCnt > 8, ui: " + ui + ", chkUiSeq: " + chkUiSeq);
                    if (chkUiSeq) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.getFragmentChange().onFragmentChange(UiSeq.ADMIN_PASS,"ADMIN_PASS",null);
                            }
                        });
                    }
                    clickedCnt = 0;
                } catch (Exception e) {
                    logger.error("btnLogo error : {}", e.getMessage());
                }
            }
            clickedCnt++;
        } else if (Objects.equals(v.getId(), R.id.btnHome)) {
            activity.getClassUiProcess().onHome();
        }
    }
}