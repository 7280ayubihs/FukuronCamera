package jp.co.jex.fukuroncamera;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class ProgressDialogFragment extends DialogFragment {

    private static class ArgumentKey {
        /*package-private*/ static final String MESSAGE = "message";
    }

    public static ProgressDialogFragment newInstance(CharSequence message) {
        ProgressDialogFragment dialogFragment = new ProgressDialogFragment();
        dialogFragment.setCancelable(false);
        Bundle args = new Bundle();
        args.putCharSequence(ArgumentKey.MESSAGE, message);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //return super.onCreateDialog(savedInstanceState);
        View view = ((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.dialog_progress, null);
        ((TextView)view.findViewById(R.id.message)).setText(getArguments().getCharSequence(ArgumentKey.MESSAGE));
        return new AlertDialog.Builder(getActivity()).setView(view).create();
    }
}
